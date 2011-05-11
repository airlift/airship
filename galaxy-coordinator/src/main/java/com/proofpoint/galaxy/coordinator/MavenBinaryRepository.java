package com.proofpoint.galaxy.coordinator;

import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableList.Builder;
import com.google.common.collect.Lists;
import com.google.common.io.ByteProcessor;
import com.google.common.io.ByteStreams;
import com.google.common.io.InputSupplier;
import com.google.common.io.Resources;
import com.google.inject.Inject;
import com.proofpoint.galaxy.shared.BinarySpec;

import java.io.InputStream;
import java.net.URI;
import java.util.List;

public class MavenBinaryRepository implements BinaryRepository
{
    private final List<URI> binaryRepositoryBases;

    public MavenBinaryRepository(URI binaryRepositoryBase, URI... binaryRepositoryBases)
    {
        this.binaryRepositoryBases = ImmutableList.<URI>builder().add(binaryRepositoryBase).add(binaryRepositoryBases).build();
    }

    public MavenBinaryRepository(Iterable<URI> binaryRepositoryBases)
    {
        this.binaryRepositoryBases = ImmutableList.copyOf(binaryRepositoryBases);
    }

    @Inject
    public MavenBinaryRepository(CoordinatorConfig config)
    {
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
        List<URI> checkedUris = Lists.newArrayList();
        for (URI binaryRepositoryBase : binaryRepositoryBases) {
            String fileVersion = binarySpec.getVersion();
            if (binarySpec.getVersion().contains("SNAPSHOT")) {
                StringBuilder builder = new StringBuilder();
                builder.append(binarySpec.getGroupId().replace('.', '/')).append('/');
                builder.append(binarySpec.getArtifactId()).append('/');
                builder.append(binarySpec.getVersion()).append('/');
                builder.append("maven-metadata.xml");

                URI uri = binaryRepositoryBase.resolve(builder.toString());
                try {
                    MavenMetadata metadata = MavenMetadata.unmarshalMavenMetadata(Resources.toString(uri.toURL(), Charsets.UTF_8));
                    fileVersion = String.format("%s-%s-%s",
                            binarySpec.getVersion().replaceAll("-SNAPSHOT", ""),
                            metadata.versioning.snapshot.timestamp,
                            metadata.versioning.snapshot.buildNumber);
                }
                catch (Exception ignored) {
                    // no maven-metadata.xml file... hope this is laid out normally
                }

            }

            StringBuilder builder = new StringBuilder();
            builder.append(binarySpec.getGroupId().replace('.', '/')).append('/');
            builder.append(binarySpec.getArtifactId()).append('/');
            builder.append(binarySpec.getVersion()).append('/');
            builder.append(binarySpec.getArtifactId()).append('-').append(fileVersion);
            if (binarySpec.getClassifier() != null) {
                builder.append('-').append(binarySpec.getClassifier());
            }
            builder.append('.').append(binarySpec.getPackaging());

            URI uri = binaryRepositoryBase.resolve(builder.toString());
            checkedUris.add(uri);
            if (isValidBinary(uri)) {
                return uri;
            }
        }
        throw new RuntimeException("Unable to find binary " + binarySpec + " at " + checkedUris);
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
