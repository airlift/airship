package io.airlift.airship.coordinator;

import com.amazonaws.AmazonServiceException;
import com.amazonaws.HttpMethod;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.AbortMultipartUploadRequest;
import com.amazonaws.services.s3.model.CompleteMultipartUploadRequest;
import com.amazonaws.services.s3.model.InitiateMultipartUploadRequest;
import com.amazonaws.services.s3.model.InitiateMultipartUploadResult;
import com.amazonaws.services.s3.model.UploadPartRequest;
import com.amazonaws.services.s3.model.UploadPartResult;
import com.google.common.base.Preconditions;
import com.google.common.base.Throwables;
import com.google.inject.Inject;
import io.airlift.airship.shared.WritableRepository;
import org.apache.commons.lang.StringUtils;
import org.joda.time.DateTime;

import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;

import static io.airlift.airship.shared.MavenCoordinates.DEFAULT_BINARY_PACKAGING;
import static io.airlift.airship.shared.MavenCoordinates.DEFAULT_CONFIG_PACKAGING;

/**
 * A WritableRepository implemented on top of S3.  This repository does not support updating by just a version,
 * it requires the full artifact identifier to be specified.
 */
public class S3Repository
        implements WritableRepository
{
    private final AmazonS3 s3Client;
    private final String bucket;
    private final String keyPrefix;

    @Inject
    public S3Repository(
            AmazonS3 s3Client,
            CoordinatorConfig config
    )
    {
        this(s3Client, config.getS3RepoBucket(), config.getS3RepoKeyPrefix());
    }

    public S3Repository(
            AmazonS3 s3Client,
            String bucket,
            String keyPrefix
    )
    {
        this.s3Client = Preconditions.checkNotNull(s3Client, "s3Client was null");
        this.bucket = Preconditions.checkNotNull(bucket, "bucket was null");
        this.keyPrefix = keyPrefix == null
                ? ""
                : (keyPrefix.endsWith("/") ? keyPrefix : StringUtils.stripEnd(keyPrefix, "/")); // no slashes at end of prefix
    }

    @Override
    public String configShortName(String config)
    {
        if (!config.startsWith("@")) {
            return null;
        }
        config = config.substring(1);

        return config;
    }

    @Override
    public String configRelativize(String config)
    {
        if (!config.startsWith("@")) {
            return null;
        }
        return null;
    }

    @Override
    public String configResolve(String config)
    {
        if (!config.startsWith("@")) {
            return null;
        }
        config = config.substring(1);

        URI uri = toHttpUri(config, DEFAULT_CONFIG_PACKAGING);
        if (uri != null) {
            return "@" + config;
        }
        return null;
    }

    @Override
    public String configUpgrade(String config, String version)
    {
        if (!config.startsWith("@") || !version.startsWith("@")) {
            return null;
        }
        version = version.substring(1);
        String upgrade = upgrade(version, DEFAULT_CONFIG_PACKAGING);
        if (upgrade != null) {
            return "@" + upgrade;
        }
        return null;
    }

    @Override
    public boolean configEqualsIgnoreVersion(String config1, String config2)
    {
        return config1.startsWith("@") &&
                config2.startsWith("@") &&
                config1.equals(config2);
    }

    @Override
    public URI configToHttpUri(String config)
    {
        if (!config.startsWith("@")) {
            return null;
        }
        config = config.substring(1);
        return toHttpUri(config, DEFAULT_CONFIG_PACKAGING);
    }

    @Override
    public String binaryRelativize(String binary)
    {
        return null;
    }

    @Override
    public String binaryResolve(String binary)
    {
        URI uri = toHttpUri(binary, DEFAULT_BINARY_PACKAGING);
        if (uri != null) {
            return binary;
        }
        return null;
    }

    @Override
    public String binaryUpgrade(String binary, String version)
    {
        return upgrade(version, DEFAULT_BINARY_PACKAGING);
    }

    @Override
    public boolean binaryEqualsIgnoreVersion(String binary1, String binary2)
    {
        return binary1.equals(binary2);
    }

    @Override
    public URI binaryToHttpUri(String binary)
    {
        return toHttpUri(binary, DEFAULT_BINARY_PACKAGING);
    }

    private String upgrade(String version, String defaultPackaging)
    {
        // version pattern did not match, so check if new version is an absolute uri
        URI uri = toHttpUri(version, defaultPackaging);
        if (uri != null) {
            return version;
        }
        return null;
    }

    private URI toHttpUri(String path, String defaultExtension)
    {
        String key = makeKey(path);
        if (exists(key)) {
            return makeUri(key);
        }

        key = key + "." + defaultExtension;
        if (exists(key)) {
            return makeUri(key);
        }

        return null;
    }

    private String makeKey(String path)
    {
        if (path.startsWith("/")) {
            return keyPrefix + path;
        }
        return keyPrefix + "/" + path;
    }

    private boolean exists(final String key)
    {
        try {
            s3Client.getObjectMetadata(bucket, key);
            return true;
        }
        catch (AmazonServiceException e) {
            return false;
        }
    }

    private URI makeUri(String key)
    {
        try {
            return s3Client.generatePresignedUrl(bucket, key, new DateTime().plusMinutes(15).toDate(), HttpMethod.GET)
                    .toURI();
        }
        catch (URISyntaxException e) {
            throw Throwables.propagate(e);
        }
    }

    @Override
    public String toString()
    {
        final StringBuilder sb = new StringBuilder();
        sb.append("S3Repository");
        sb.append("{bucket=").append(bucket);
        sb.append(", keyPrefix=").append(keyPrefix);
        sb.append('}');
        return sb.toString();
    }

    @Override
    public void put(String key, InputStream inputStream)
    {
        key = makeKey(key);

        final InitiateMultipartUploadResult multipart = s3Client.initiateMultipartUpload(
                new InitiateMultipartUploadRequest(bucket, key)
        );

        try {
            final UploadPartResult partResult = s3Client.uploadPart(
                    new UploadPartRequest().withBucketName(bucket)
                            .withKey(key)
                            .withLastPart(true)
                            .withInputStream(inputStream)
                            .withUploadId(multipart.getUploadId())
            );

            s3Client.completeMultipartUpload(
                    new CompleteMultipartUploadRequest(bucket, key, multipart.getUploadId(), Arrays.asList(partResult.getPartETag()))
            );
        }
        catch (RuntimeException e) {
            s3Client.abortMultipartUpload(new AbortMultipartUploadRequest(bucket, key, multipart.getUploadId()));
        }
    }
}
