package com.proofpoint.galaxy;

import com.google.common.base.Charsets;
import com.google.common.io.Resources;
import com.google.inject.Inject;

import java.net.URI;

import static com.proofpoint.galaxy.DeploymentUtils.toURL;
import static com.proofpoint.galaxy.MavenMetadata.unmarshalMavenMetadata;

public class MavenBinaryRepository implements BinaryRepository
{
    private final URI binaryRepositoryBase;

    public MavenBinaryRepository(URI binaryRepositoryBase)
    {
        this.binaryRepositoryBase = binaryRepositoryBase;
    }

    @Inject
    public MavenBinaryRepository(ConsoleConfig config)
    {
        binaryRepositoryBase = URI.create(config.getBinaryRepoBase());
    }

    @Override
    public URI getBinaryUri(BinarySpec binarySpec)
    {
        String fileVersion = binarySpec.getVersion();
        if (binarySpec.getVersion().contains("SNAPSHOT")) {
            StringBuilder builder = new StringBuilder();
            builder.append(binarySpec.getGroupId().replace('.', '/')).append('/');
            builder.append(binarySpec.getArtifactId()).append('/');
            builder.append(binarySpec.getVersion()).append('/');
            builder.append("maven-metadata.xml");

            URI uri = binaryRepositoryBase.resolve(builder.toString());
            try {
                MavenMetadata metadata = unmarshalMavenMetadata(Resources.toString(toURL(uri), Charsets.UTF_8));
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
            builder.append('-').append(binarySpec.getArtifactId());
        }
        builder.append('.').append(binarySpec.getPackaging());

        return binaryRepositoryBase.resolve(builder.toString());
    }
}
