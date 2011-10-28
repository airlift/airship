package com.proofpoint.galaxy.coordinator.auth;

import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.model.ListObjectsRequest;
import com.amazonaws.services.s3.model.ObjectListing;
import com.amazonaws.services.s3.model.S3Object;
import com.amazonaws.services.s3.model.S3ObjectSummary;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Charsets;
import com.google.common.base.Function;
import com.google.common.collect.AbstractLinkedIterator;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterators;
import com.google.common.io.CharStreams;
import com.google.common.io.InputSupplier;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.proofpoint.galaxy.coordinator.AwsProvisionerConfig;
import com.proofpoint.log.Logging;
import com.proofpoint.units.Duration;

import javax.annotation.Nullable;
import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.inject.Inject;
import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.collect.Lists.newArrayList;
import static com.google.common.io.CharStreams.newReaderSupplier;

public class S3AuthorizedKeyStore
        implements AuthorizedKeyStore
{
    private final AmazonS3 s3Client;
    private final String bucketName;
    private final String path;

    private final AtomicReference<Map<String, AuthorizedKey>> authorizedKeys = new AtomicReference<Map<String, AuthorizedKey>>(ImmutableMap.<String, AuthorizedKey>of());
    private Map<String, KeyFile> keyFiles = new TreeMap<String, KeyFile>();

    private final ScheduledExecutorService executor;
    private final Duration refreshInterval;

    @Inject
    public S3AuthorizedKeyStore(AmazonS3 s3Client, AwsProvisionerConfig awsProvisionerConfig)
    {
        this(s3Client, awsProvisionerConfig.getS3KeystoreBucket(), awsProvisionerConfig.getS3KeystorePath(), awsProvisionerConfig.getS3KeystoreRefreshInterval());
    }

    public S3AuthorizedKeyStore(AmazonS3 s3Client, String bucketName, String path, Duration refreshInterval)
    {
        this.s3Client = checkNotNull(s3Client);
        this.bucketName = checkNotNull(bucketName);
        if (!path.endsWith("/")) {
            path = path + "/";
        }
        this.path = checkNotNull(path);
        this.refreshInterval = checkNotNull(refreshInterval);
        executor = Executors.newSingleThreadScheduledExecutor(new ThreadFactoryBuilder().setDaemon(true).setNameFormat("S3AuthorizedKeyStore-%s").build());
        refreshKeys();
    }

    @PostConstruct
    public void start()
    {
        executor.scheduleWithFixedDelay(new Runnable()
        {
            @Override
            public void run()
            {
                refreshKeys();
            }
        }, 0, (long) refreshInterval.toMillis(), TimeUnit.MILLISECONDS);
    }

    @PreDestroy
    public void stop()
    {
        executor.shutdownNow();
    }

    @Override
    public AuthorizedKey get(byte[] fingerprint)
    {
        return authorizedKeys.get().get(new String(fingerprint, Charsets.UTF_8));
    }

    @VisibleForTesting
    synchronized ImmutableMap<String, AuthorizedKey> refreshKeys()
    {
        ImmutableMap.Builder<String, AuthorizedKey> newAuthorizedKeys = ImmutableMap.builder();
        Map<String, KeyFile> newKeyFiles = new TreeMap<String, KeyFile>();
        for (S3ObjectSummary objectSummary : new S3ObjectListing(s3Client, new ListObjectsRequest(bucketName, path, null, "/", null))) {
            KeyFile keyFile = keyFiles.get(objectSummary.getKey());

            // only load s3 data if the file is new or has changed
            if (keyFile == null || !keyFile.etag.equals(objectSummary.getETag())) {
                try {
                    String userId = objectSummary.getKey().substring(path.length());
                    if (userId.endsWith(".pub")) {
                        userId = userId.substring(0, userId.length() - ".pub".length());
                    }

                    List<AuthorizedKey> keys = newArrayList();
                    for (String line : CharStreams.readLines(newReaderSupplier(new S3InputSupplier(s3Client, bucketName, objectSummary.getKey()), Charsets.UTF_8))) {
                        line = line.trim();
                        if (!line.isEmpty()) {
                            PublicKey key = PublicKey.valueOf(line);
                            keys.add(new AuthorizedKey(userId, key));
                        }
                    }
                    keyFile = new KeyFile(objectSummary.getKey(), objectSummary.getETag(), keys);
                }
                catch (IOException e) {
                    // assume key file was removed between listing and fetch
                    keyFile = null;
                }
                catch (Exception e) {
                    // corrupt key file
                    // todo warn?
                    keyFile = null;
                }
            }

            if (keyFile != null) {
                newKeyFiles.put(keyFile.s3Key, keyFile);
                for (AuthorizedKey authorizedKey : keyFile.authorizedKeys) {
                    newAuthorizedKeys.put(new String(authorizedKey.getPublicKey().getFingerprint(), Charsets.UTF_8), authorizedKey);
                }
            }
        }
        keyFiles = newKeyFiles;
        authorizedKeys.set(newAuthorizedKeys.build());

        return newAuthorizedKeys.build();
    }

    private static class KeyFile
    {
        private final String s3Key;
        private final String etag;
        private final List<AuthorizedKey> authorizedKeys;

        private KeyFile(String s3Key, String etag, List<AuthorizedKey> authorizedKeys)
        {
            this.s3Key = s3Key;
            this.etag = etag;
            this.authorizedKeys = authorizedKeys;
        }

        @Override
        public String toString()
        {
            final StringBuilder sb = new StringBuilder();
            sb.append("KeyFile");
            sb.append("{s3Key='").append(s3Key).append('\'');
            sb.append(", etag='").append(etag).append('\'');
            sb.append(", authorizedKeys=").append(authorizedKeys);
            sb.append('}');
            return sb.toString();
        }
    }

    private static class S3ObjectListing implements Iterable<S3ObjectSummary>
    {
        private final AmazonS3 s3Client;
        private final ListObjectsRequest listObjectsRequest;

        public S3ObjectListing(AmazonS3 s3Client, ListObjectsRequest listObjectsRequest)
        {
            this.s3Client = s3Client;
            this.listObjectsRequest = listObjectsRequest;
        }

        @Override
        public Iterator<S3ObjectSummary> iterator()
        {
            Iterator<ObjectListing> objectListings = new AbstractLinkedIterator<ObjectListing>(s3Client.listObjects(listObjectsRequest))
            {
                @Override
                protected ObjectListing computeNext(ObjectListing previous)
                {
                    if (!previous.isTruncated()) {
                        return null;
                    }
                    return s3Client.listNextBatchOfObjects(previous);
                }
            };

            return Iterators.concat(Iterators.transform(objectListings, new Function<ObjectListing, Iterator<S3ObjectSummary>>()
            {
                @Override
                public Iterator<S3ObjectSummary> apply(@Nullable ObjectListing input)
                {
                    return input.getObjectSummaries().iterator();
                }
            }));
        }
    }

    private static class S3InputSupplier implements InputSupplier<InputStream>
    {
        private final AmazonS3 s3Client;
        private final String bucketName;
        private final String key;

        public S3InputSupplier(AmazonS3 s3Client, String bucketName, String key)
        {
            this.s3Client = s3Client;
            this.bucketName = bucketName;
            this.key = key;
        }

        @Override
        public InputStream getInput()
                throws IOException
        {
            S3Object object = s3Client.getObject(bucketName, key);
            return object.getObjectContent();
        }
    }
}
