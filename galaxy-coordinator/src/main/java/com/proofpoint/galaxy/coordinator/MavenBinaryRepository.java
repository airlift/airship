package com.proofpoint.galaxy.coordinator;

import com.google.common.base.Charsets;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableList.Builder;
import com.google.common.io.ByteProcessor;
import com.google.common.io.ByteStreams;
import com.google.common.io.InputSupplier;
import com.google.common.io.Resources;
import com.google.inject.Inject;
import com.proofpoint.galaxy.shared.BinarySpec;

import java.io.InputStream;
import java.net.URI;
import java.util.List;

import static com.google.common.collect.Lists.newArrayList;

public class MavenBinaryRepository implements BinaryRepository
{
    private final List<String> defaultGroupIds;
    private final List<URI> binaryRepositoryBases;

    public MavenBinaryRepository(Iterable<String> defaultGroupIds, URI binaryRepositoryBase, URI... binaryRepositoryBases)
    {
        this.defaultGroupIds = ImmutableList.copyOf(defaultGroupIds);
        this.binaryRepositoryBases = ImmutableList.<URI>builder().add(binaryRepositoryBase).add(binaryRepositoryBases).build();
    }

    public MavenBinaryRepository(Iterable<String> defaultGroupIds, Iterable<URI> binaryRepositoryBases)
    {
        this.defaultGroupIds = ImmutableList.copyOf(defaultGroupIds);
        this.binaryRepositoryBases = ImmutableList.copyOf(binaryRepositoryBases);
    }

    @Inject
    public MavenBinaryRepository(CoordinatorConfig config)
            throws Exception
    {
        this.defaultGroupIds = ImmutableList.copyOf(Splitter.on(",").trimResults().omitEmptyStrings().split(config.getConfigRepositoryGroupId()));

        Builder<URI> builder = ImmutableList.builder();

        for (String binaryRepoBase : config.getBinaryRepoBases()) {
            if (!binaryRepoBase.endsWith("/")) {
                binaryRepoBase = binaryRepoBase + "/";
            }
            builder.add(URI.create(binaryRepoBase));
        }
        binaryRepositoryBases = builder.build();
    }

    @Override
    public URI getBinaryUri(BinarySpec binarySpec)
    {
        // resolve binary spec groupId or snapshot version
        BinarySpec resolvedUri = resolveBinarySpec(binarySpec);

        List<URI> checkedUris = newArrayList();
        for (URI binaryRepositoryBase : binaryRepositoryBases) {
            // build the uri
            StringBuilder builder = new StringBuilder();
            builder.append(resolvedUri.getGroupId().replace('.', '/')).append('/');
            builder.append(resolvedUri.getArtifactId()).append('/');
            builder.append(binarySpec.getVersion()).append('/');
            builder.append(resolvedUri.getArtifactId()).append('-').append(resolvedUri.getVersion());
            if (resolvedUri.getClassifier() != null) {
                builder.append('-').append(resolvedUri.getClassifier());
            }
            builder.append('.').append(resolvedUri.getPackaging());

            URI uri = binaryRepositoryBase.resolve(builder.toString());

            // try to download some of the file
            if (isValidBinary(uri)) {
                return uri;
            }

            checkedUris.add(uri);
        }
        throw new RuntimeException("Unable to find binary " + resolvedUri + " at " + checkedUris);
    }

    public BinarySpec resolveBinarySpec(BinarySpec binarySpec)
    {
        if (binarySpec.isResolved()) {
            return binarySpec;
        }

        List<String> groupIds;
        if (binarySpec.getGroupId() != null) {
            groupIds = ImmutableList.of(binarySpec.getGroupId());
        }
        else {
            groupIds = defaultGroupIds;
        }

        List<BinarySpec> binarySpecs = newArrayList();
        for (String groupId : groupIds) {
            if (binarySpec.getVersion().contains("SNAPSHOT")) {
                StringBuilder builder = new StringBuilder();
                builder.append(groupId.replace('.', '/')).append('/');
                builder.append(binarySpec.getArtifactId()).append('/');
                builder.append(binarySpec.getVersion()).append('/');
                builder.append("maven-metadata.xml");

                for (URI binaryRepositoryBase : binaryRepositoryBases) {
                    URI uri = binaryRepositoryBase.resolve(builder.toString());
                    try {
                        MavenMetadata metadata = MavenMetadata.unmarshalMavenMetadata(Resources.toString(uri.toURL(), Charsets.UTF_8));
                        String version = String.format("%s-%s-%s",
                                binarySpec.getVersion().replaceAll("-SNAPSHOT", ""),
                                metadata.versioning.snapshot.timestamp,
                                metadata.versioning.snapshot.buildNumber);
                        binarySpecs.add(new BinarySpec(groupId, binarySpec.getArtifactId(), version, binarySpec.getPackaging(), binarySpec.getClassifier()));
                    }
                    catch (Exception ignored) {
                        // no maven-metadata.xml file... hope this is laid out normally
                    }
                }
            }
            else {
                StringBuilder builder = new StringBuilder();
                builder.append(groupId.replace('.', '/')).append('/');
                builder.append(binarySpec.getArtifactId()).append('/');
                builder.append("maven-metadata.xml");

                for (URI binaryRepositoryBase : binaryRepositoryBases) {
                    URI uri = binaryRepositoryBase.resolve(builder.toString());
                    try {
                        MavenMetadata metadata = MavenMetadata.unmarshalMavenMetadata(Resources.toString(uri.toURL(), Charsets.UTF_8));
                        if (metadata.versioning.versions.contains(binarySpec.getVersion())) {
                            binarySpecs.add(new BinarySpec(groupId, binarySpec.getArtifactId(), binarySpec.getVersion(), binarySpec.getPackaging(), binarySpec.getClassifier()));
                        }
                    }
                    catch (Exception ignored) {
                        // no maven-metadata.xml file... hope this is laid out normally
                    }
                }

            }
        }

        if (binarySpecs.size() > 1) {
            throw new RuntimeException("Ambiguous spec " + binarySpec + "  matched " + binarySpecs);
        }
        if (binarySpecs.isEmpty()) {
            if (binarySpec.getGroupId() != null) {
                return binarySpec;
            }
            throw new RuntimeException("Unable to find " + binarySpec + " at " + binaryRepositoryBases);
        }
        return binarySpecs.get(0);
    }

    private boolean isValidBinary(URI uri)
    {
        try {
            InputSupplier<InputStream> inputSupplier = Resources.newInputStreamSupplier(uri.toURL());
            ByteStreams.readBytes(inputSupplier, new ByteProcessor<Void>()
            {
                private int count;

                public boolean processBytes(byte[] buffer, int offset, int length)
                {
                    count += length;
                    // make sure we got at least 10 bytes
                    return count < 10;
                }

                public Void getResult()
                {
                    return null;
                }
            });
            return true;
        }
        catch (Exception ignored) {
        }
        return false;
    }
}
