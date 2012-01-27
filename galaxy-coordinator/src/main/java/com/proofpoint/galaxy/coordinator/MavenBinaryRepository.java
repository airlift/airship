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
import com.proofpoint.galaxy.coordinator.MavenMetadata.SnapshotVersion;
import com.proofpoint.galaxy.shared.BinarySpec;

import java.io.InputStream;
import java.net.URI;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.google.common.collect.Lists.newArrayList;

public class MavenBinaryRepository implements BinaryRepository
{
    private static final Pattern TIMESTAMP_VERSION = Pattern.compile("^(.+)-[0-9]{8}\\.[0-9]{6}\\-[0-9]$");
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
        binarySpec = resolveBinarySpec(binarySpec);

        List<URI> checkedUris = newArrayList();
        for (URI binaryRepositoryBase : binaryRepositoryBases) {
            // build the uri
            StringBuilder builder = new StringBuilder();
            builder.append(binarySpec.getGroupId().replace('.', '/')).append('/');
            builder.append(binarySpec.getArtifactId()).append('/');
            builder.append(binarySpec.getVersion()).append('/');
            builder.append(binarySpec.getArtifactId()).append('-').append(binarySpec.getFileVersion());
            if (binarySpec.getClassifier() != null) {
                builder.append('-').append(binarySpec.getClassifier());
            }
            builder.append('.').append(binarySpec.getPackaging());

            URI uri = binaryRepositoryBase.resolve(builder.toString());

            // try to download some of the file
            if (isValidBinary(uri)) {
                return uri;
            }

            checkedUris.add(uri);
        }
        throw new RuntimeException("Unable to find binary " + binarySpec + " at " + checkedUris);
    }

    @Override
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
            boolean foundTimestampVersion = false;
            if (binarySpec.getVersion().contains("SNAPSHOT")) {

                for (URI binaryRepositoryBase : binaryRepositoryBases) {
                    try {
                        MavenMetadata metadata = loadMavenMetadata(binaryRepositoryBase,
                                groupId,
                                binarySpec.getArtifactId(),
                                binarySpec.getVersion());

                        String fileVersion = String.format("%s-%s-%s",
                                binarySpec.getVersion().replaceAll("-SNAPSHOT", ""),
                                metadata.versioning.snapshot.timestamp,
                                metadata.versioning.snapshot.buildNumber);
                        binarySpecs.add(new BinarySpec(groupId,
                                binarySpec.getArtifactId(),
                                binarySpec.getVersion(),
                                binarySpec.getPackaging(),
                                binarySpec.getClassifier(),
                                fileVersion));
                        foundTimestampVersion = true;
                    }
                    catch (Exception ignored) {
                        // no maven-metadata.xml file... hope this is laid out normally
                    }
                }

                // if we could not resolve the timestamp version, try resolving
                // artifact with "SNAPSHOT" version since some old maven repositories
                // don't replace SNAPSHOTS with timestamps
                if (foundTimestampVersion) {
                    continue;
                }
            }

            for (URI binaryRepositoryBase : binaryRepositoryBases) {
                try {
                    MavenMetadata metadata = loadMavenMetadata(binaryRepositoryBase, groupId, binarySpec.getArtifactId(), null);
                    if (metadata.versioning.versions.contains(binarySpec.getVersion())) {
                        binarySpecs.add(new BinarySpec(groupId,
                                binarySpec.getArtifactId(),
                                binarySpec.getVersion(),
                                binarySpec.getPackaging(),
                                binarySpec.getClassifier(),
                                binarySpec.getFileVersion()));
                        foundTimestampVersion = true;
                    }
                }
                catch (Exception ignored) {
                    // no maven-metadata.xml file... hope this is laid out normally
                }
            }
            if (foundTimestampVersion) {
                continue;
            }

            // Snapshot revisions are resolved to timestamp version which may need to be converted back to SNAPSHOT for resolution
            Matcher timestampMatcher = TIMESTAMP_VERSION.matcher(binarySpec.getVersion());
            if (timestampMatcher.matches()) {
                String version = timestampMatcher.group(1) + "-SNAPSHOT";

                for (URI binaryRepositoryBase : binaryRepositoryBases) {
                    try {
                        MavenMetadata metadata = loadMavenMetadata(binaryRepositoryBase, groupId, binarySpec.getArtifactId(), version);
                        for (SnapshotVersion snapshotVersion : metadata.versioning.snapshotVersions) {
                            if (snapshotVersion.value.equals(binarySpec.getVersion())) {
                                binarySpecs.add(new BinarySpec(groupId,
                                        binarySpec.getArtifactId(),
                                        version,
                                        binarySpec.getPackaging(),
                                        binarySpec.getClassifier(),
                                        binarySpec.getVersion()));
                            }
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
            // Some maven repositories don't have maven metadata so as long as the groupId is resolved
            // just continue
            if (binarySpec.getGroupId() != null) {
                return binarySpec;
            }
            throw new RuntimeException("Unable to find " + binarySpec + " at " + binaryRepositoryBases);
        }
        return binarySpecs.get(0);
    }

    private MavenMetadata loadMavenMetadata(URI repositoryBase, String groupId, String artifactId, String version)
            throws Exception
    {
        StringBuilder builder = new StringBuilder();
        builder.append(groupId.replace('.', '/')).append('/');
        builder.append(artifactId).append('/');
        if (version != null) {
            builder.append(version).append('/');
        }
        builder.append("maven-metadata.xml");

        URI uri = repositoryBase.resolve(builder.toString());
        return MavenMetadata.unmarshalMavenMetadata(Resources.toString(uri.toURL(), Charsets.UTF_8));
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
