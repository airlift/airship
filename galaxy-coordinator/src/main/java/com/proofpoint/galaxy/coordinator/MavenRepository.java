package com.proofpoint.galaxy.coordinator;

import com.google.common.base.Objects;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableList.Builder;
import com.google.common.io.ByteProcessor;
import com.google.common.io.ByteStreams;
import com.google.common.io.CharStreams;
import com.google.common.io.InputSupplier;
import com.google.common.io.Resources;
import com.google.inject.Inject;
import com.proofpoint.galaxy.coordinator.MavenMetadata.SnapshotVersion;
import com.proofpoint.galaxy.shared.MavenCoordinates;
import com.proofpoint.galaxy.shared.Repository;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.URLConnection;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.google.common.base.Charsets.UTF_8;
import static com.google.common.collect.Lists.newArrayList;
import static com.proofpoint.galaxy.shared.MavenCoordinates.toBinaryGAV;
import static com.proofpoint.galaxy.shared.MavenCoordinates.toConfigGAV;

public class MavenRepository implements Repository
{
    private static final Pattern TIMESTAMP_VERSION = Pattern.compile("^(.+)-[0-9]{8}\\.[0-9]{6}\\-[0-9]+$");
    private final List<String> defaultGroupIds;
    private final List<URI> repositoryBases;

    public MavenRepository(Iterable<String> defaultGroupIds, URI repositoryBase, URI... repositoryBases)
    {
        this(defaultGroupIds, ImmutableList.<URI>builder().add(repositoryBase).add(repositoryBases).build());
    }

    public MavenRepository(Iterable<String> defaultGroupIds, Iterable<URI> repositoryBases)
    {
        this.defaultGroupIds = ImmutableList.copyOf(defaultGroupIds);
        for (URI uri : repositoryBases) {
            Preconditions.checkArgument(uri.toASCIIString().endsWith("/"), "Uri must end with a '/' " + uri);
        }
        this.repositoryBases = ImmutableList.copyOf(repositoryBases);
    }

    @Inject
    public MavenRepository(CoordinatorConfig config)
    {
        if (config.getDefaultRepositoryGroupId() != null) {
            this.defaultGroupIds = ImmutableList.copyOf(config.getDefaultRepositoryGroupId());
        } else {
            this.defaultGroupIds = ImmutableList.of();
        }

        Builder<URI> builder = ImmutableList.builder();
        for (String binaryRepoBase : config.getRepositories()) {
            if (!binaryRepoBase.endsWith("/")) {
                binaryRepoBase = binaryRepoBase + "/";
            }
            builder.add(URI.create(binaryRepoBase));
        }
        repositoryBases = builder.build();
    }

    @Override
    public String configResolve(String config)
    {
        MavenCoordinates coordinates = MavenCoordinates.fromConfigGAV(config);
        if (coordinates == null) {
            return null;
        }
        coordinates = resolve(coordinates);
        if (coordinates != null) {
            return toConfigGAV(coordinates);
        }
        return null;
    }

    @Override
    public String configShortName(String config)
    {
        if (!config.startsWith("@")) {
            return null;
        }

        MavenCoordinates coordinates = MavenCoordinates.fromConfigGAV(config);
        if (coordinates == null) {
            return config.substring(0).replaceAll(":", "_");
        }
        return coordinates.getArtifactId();
    }

    @Override
    public String configUpgrade(String config, String version)
    {
        if (!version.startsWith("@")) {
            return null;
        }

        MavenCoordinates coordinates = MavenCoordinates.fromConfigGAV(config);
        if (coordinates == null) {
            return null;
        }

        coordinates = new MavenCoordinates(coordinates.getGroupId(),
                coordinates.getArtifactId(),
                version.substring(1),
                coordinates.getPackaging(),
                coordinates.getClassifier(),
                null);

        coordinates = resolve(coordinates);
        if (coordinates != null) {
            return MavenCoordinates.toConfigGAV(coordinates);
        }
        return configResolve(version);
    }

    @Override
    public boolean configEqualsIgnoreVersion(String config1, String config2)
    {
        MavenCoordinates coordinates1 = MavenCoordinates.fromConfigGAV(config1);
        MavenCoordinates coordinates2 = MavenCoordinates.fromConfigGAV(config2);
        return coordinates1 != null &&
                coordinates2 != null &&
                coordinates1.equalsIgnoreVersion(coordinates2);

    }

    @Override
    public URI configToHttpUri(String config)
    {
        MavenCoordinates coordinates = MavenCoordinates.fromConfigGAV(config);
        if (coordinates == null) {
            return null;
        }
        return toHttpUri(coordinates, true);
    }

    @Override
    public String binaryResolve(String binary)
    {
        MavenCoordinates coordinates = MavenCoordinates.fromBinaryGAV(binary);
        if (coordinates == null) {
            return null;
        }
        coordinates = resolve(coordinates);
        if (coordinates != null) {
            return toBinaryGAV(coordinates);
        }
        return null;
    }

    @Override
    public String binaryUpgrade(String binary, String version)
    {
        MavenCoordinates coordinates = MavenCoordinates.fromBinaryGAV(binary);
        if (coordinates == null) {
            return null;
        }
        coordinates = new MavenCoordinates(coordinates.getGroupId(),
                coordinates.getArtifactId(),
                version,
                coordinates.getPackaging(),
                coordinates.getClassifier(),
                null);

        coordinates = resolve(coordinates);
        if (coordinates != null) {
            return MavenCoordinates.toBinaryGAV(coordinates);
        }
        return binaryResolve(version);
    }

    @Override
    public boolean binaryEqualsIgnoreVersion(String binary1, String binary2)
    {
        MavenCoordinates coordinates1 = MavenCoordinates.fromBinaryGAV(binary1);
        MavenCoordinates coordinates2 = MavenCoordinates.fromBinaryGAV(binary2);
        return coordinates1 != null &&
                coordinates2 != null &&
                coordinates1.equalsIgnoreVersion(coordinates2);
    }

    @Override
    public URI binaryToHttpUri(String binary)
    {
        MavenCoordinates coordinates = MavenCoordinates.fromBinaryGAV(binary);
        if (coordinates == null) {
            return null;
        }
        return toHttpUri(coordinates, true);
    }

    public URI toHttpUri(MavenCoordinates coordinates, boolean required)
    {
        // resolve binary spec groupId or snapshot version
        coordinates = resolve(coordinates);
        if (coordinates == null) {
            return null;
        }
        List<URI> checkedUris = newArrayList();
        for (URI repositoryBase : repositoryBases) {
            // build the uri
            StringBuilder builder = new StringBuilder();
            builder.append(coordinates.getGroupId().replace('.', '/')).append('/');
            builder.append(coordinates.getArtifactId()).append('/');
            builder.append(coordinates.getVersion()).append('/');
            builder.append(coordinates.getArtifactId()).append('-').append(coordinates.getFileVersion());
            if (coordinates.getClassifier() != null) {
                builder.append('-').append(coordinates.getClassifier());
            }
            builder.append('.').append(coordinates.getPackaging());

            URI uri = repositoryBase.resolve(builder.toString());

            // try to download some of the file
            if (isValidBinary(uri)) {
                return uri;
            }

            checkedUris.add(uri);
        }
        if (required) {
            throw new RuntimeException("Unable to find binary " + coordinates + " at " + checkedUris);
        }
        else {
            return null;
        }
    }

    public MavenCoordinates resolve(MavenCoordinates coordinates)
    {
        if (coordinates.isResolved()) {
            return coordinates;
        }

        List<String> groupIds;
        if (coordinates.getGroupId() != null) {
            groupIds = ImmutableList.of(coordinates.getGroupId());
        }
        else {
            groupIds = defaultGroupIds;
        }

        List<MavenCoordinates> matchedCoordinates = newArrayList();
        for (String groupId : groupIds) {
            // check for a file with the exact name
            MavenCoordinates resolvedSpec = new MavenCoordinates(groupId,
                    coordinates.getArtifactId(),
                    coordinates.getVersion(),
                    coordinates.getPackaging(),
                    coordinates.getClassifier(),
                    coordinates.getFileVersion());

            if (toHttpUri(resolvedSpec, false) != null) {
                matchedCoordinates.add(resolvedSpec);
                continue;
            }

            // check of a timestamped snapshot file
            if (coordinates.getVersion().contains("SNAPSHOT")) {
                MavenCoordinates timestampSpec = resolveSnapshotTimestamp(coordinates, groupId);
                if (timestampSpec != null) {
                    matchedCoordinates.add(timestampSpec);
                    continue;
                }
            }

            // Snapshot revisions are resolved to timestamp version which may need to be converted back to SNAPSHOT for resolution
            Matcher timestampMatcher = TIMESTAMP_VERSION.matcher(coordinates.getVersion());
            if (timestampMatcher.matches()) {
                MavenCoordinates snapshotSpec = new MavenCoordinates(groupId,
                        coordinates.getArtifactId(),
                        timestampMatcher.group(1) + "-SNAPSHOT",
                        coordinates.getPackaging(),
                        coordinates.getClassifier(),
                        coordinates.getVersion());

                if (toHttpUri(snapshotSpec, false) != null) {
                    matchedCoordinates.add(snapshotSpec);
                }
            }
        }

        if (matchedCoordinates.size() > 1) {
            throw new RuntimeException("Ambiguous spec " + coordinates + "  matched " + matchedCoordinates);
        }

        if (matchedCoordinates.isEmpty()) {
            return null;
        }

        return matchedCoordinates.get(0);
    }

    private MavenCoordinates resolveSnapshotTimestamp(MavenCoordinates coordinates, String groupId)
    {

        for (URI repositoryBase : repositoryBases) {
            try {
                // load maven metadata file
                StringBuilder builder = new StringBuilder();
                builder.append(groupId.replace('.', '/')).append('/');
                builder.append(coordinates.getArtifactId()).append('/');
                builder.append(coordinates.getVersion()).append('/');
                builder.append("maven-metadata.xml");
                URI uri = repositoryBase.resolve(builder.toString());
                MavenMetadata metadata = MavenMetadata.unmarshalMavenMetadata(toString(uri));

                for (SnapshotVersion snapshotVersion : metadata.versioning.snapshotVersions) {
                    if (coordinates.getPackaging().equals(snapshotVersion.extension) && Objects.equal(coordinates.getClassifier(), snapshotVersion.classifier)) {
                        MavenCoordinates timestampSpec = new MavenCoordinates(groupId,
                                coordinates.getArtifactId(),
                                coordinates.getVersion(),
                                coordinates.getPackaging(),
                                coordinates.getClassifier(),
                                snapshotVersion.value);

                        return timestampSpec;
                    }
                }
            }
            catch (Exception ignored) {
                // no maven-metadata.xml file... hope this is laid out normally
            }
        }
        return null;
    }

    private String toString(URI uri)
            throws IOException
    {
        final URL url = uri.toURL();
        return CharStreams.toString(new InputSupplier<InputStreamReader>() {
            @Override
            public InputStreamReader getInput()
                    throws IOException
            {
                URLConnection connection = url.openConnection();
                if (connection instanceof HttpURLConnection) {
                    HttpURLConnection httpConnection = (HttpURLConnection) connection;
                    httpConnection.addRequestProperty("User-Agent", "User-Agent: Apache-Maven/3.0.3 (Java 1.6.0_29; Mac OS X 10.7.2)");
                }
                InputStream in = connection.getInputStream();
                return new InputStreamReader(in, UTF_8);
            }
        });
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

    @Override
    public String toString()
    {
        final StringBuilder sb = new StringBuilder();
        sb.append("MavenRepository");
        sb.append("{repositoryBases=").append(repositoryBases);
        sb.append(", defaultGroupIds=").append(defaultGroupIds);
        sb.append('}');
        return sb.toString();
    }
}
