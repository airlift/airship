package io.airlift.airship.coordinator;

import com.google.common.collect.ImmutableList;
import com.google.common.io.Files;
import io.airlift.airship.coordinator.MavenMetadata.Snapshot;
import io.airlift.airship.coordinator.MavenMetadata.SnapshotVersion;
import io.airlift.airship.coordinator.MavenMetadata.Versioning;
import io.airlift.airship.shared.MavenCoordinates;
import io.airlift.airship.shared.Repository;

import java.io.File;
import java.io.FileOutputStream;
import java.net.URI;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static com.google.common.base.Charsets.UTF_8;
import static com.google.common.base.Objects.firstNonNull;
import static com.google.common.io.Files.asByteSink;
import static com.google.common.io.Resources.getResource;
import static com.google.common.io.Resources.asByteSource;
import static io.airlift.airship.shared.ArchiveHelper.createArchive;
import static io.airlift.airship.shared.FileUtils.createTempDir;
import static io.airlift.airship.shared.FileUtils.deleteRecursively;
import static io.airlift.airship.shared.FileUtils.newFile;
import static io.airlift.airship.shared.MavenCoordinates.toBinaryGAV;
import static io.airlift.airship.shared.MavenCoordinates.toConfigGAV;

public class TestingMavenRepository
        extends MavenRepository
{
    public static final Repository MOCK_REPO = new Repository()
    {
        @Override
        public String configRelativize(String config)
        {
            return null;
        }

        @Override
        public String configResolve(String config)
        {
            return config;
        }

        @Override
        public String configShortName(String config)
        {
            return config;
        }

        @Override
        public String configUpgrade(String config, String version)
        {
            MavenCoordinates coordinates = MavenCoordinates.fromConfigGAV(config);
            if (coordinates == null) {
                return null;
            }

            coordinates = new MavenCoordinates(coordinates.getGroupId(),
                    coordinates.getArtifactId(),
                    version,
                    coordinates.getPackaging(),
                    coordinates.getClassifier(),
                    null);

            return toConfigGAV(coordinates);
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
            return URI.create("fake://config/" + config);
        }

        @Override
        public String binaryRelativize(String config)
        {
            return null;
        }

        @Override
        public String binaryResolve(String binary)
        {
            return binary;
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

            return toBinaryGAV(coordinates);
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
            return URI.create("fake://binary/" + binary);
        }
    };
    private final File targetRepo;

    public TestingMavenRepository()
            throws Exception
    {
        this(createBinaryRepoDir());
    }

    public TestingMavenRepository(File targetRepo)
    {
        super(ImmutableList.<String>of("prod", "food.fruit"), targetRepo.toURI());
        this.targetRepo = targetRepo;
    }

    public File getTargetRepo()
    {
        return targetRepo;
    }

    public void destroy()
    {
        deleteRecursively(targetRepo);
    }

    public static File createBinaryRepoDir()
            throws Exception
    {
        File targetRepo = null;
        try {
            targetRepo = createTempDir("repo");

            // copy maven-metadata.xml files
            File appleMavenMetadata = new File(targetRepo, "food/fruit/apple/maven-metadata.xml");
            appleMavenMetadata.getParentFile().mkdirs();
            asByteSource(getResource(TestingMavenRepository.class, "apple-maven-metadata.xml")).copyTo(asByteSink(appleMavenMetadata));

            File bananaMavenMetadata = new File(targetRepo, "food/fruit/banana/2.0-SNAPSHOT/maven-metadata.xml");
            bananaMavenMetadata.getParentFile().mkdirs();
            asByteSource(getResource(TestingMavenRepository.class, "banana-maven-metadata.xml")).copyTo(asByteSink(bananaMavenMetadata));

            // tar up the archive and add them to the repository
            File appleArchiveV1 = new File(targetRepo, "food/fruit/apple/1.0/apple-1.0.tar.gz");
            File appleArchiveV2 = new File(targetRepo, "food/fruit/apple/2.0/apple-2.0.tar.gz");
            File bananaArchive = new File(targetRepo, "food/fruit/banana/2.0-SNAPSHOT/banana-2.0-20110311.201909-1.tar.gz");
            createArchive(appleArchiveV1);
            appleArchiveV2.getParentFile().mkdirs();
            Files.copy(appleArchiveV1, appleArchiveV2);
            bananaArchive.getParentFile().mkdirs();
            Files.copy(appleArchiveV1, bananaArchive);

            // add prod configurations
            initConfigRepo(targetRepo, "prod");

            return targetRepo;
        }
        catch (Exception e) {
            if (targetRepo != null) {
                deleteRecursively(targetRepo);
            }
            throw e;
        }
    }

    public static void initConfigRepo(File targetRepo, String groupId)
            throws Exception
    {
        createConfig(targetRepo, groupId, "apple", "1.0", null);
        createConfig(targetRepo, groupId, "apple", "2.0", null);
        createConfig(targetRepo, groupId, "banana", "2.0-SNAPSHOT", "2.0-20110311.201909-1");

        createMavenMetadata(targetRepo, groupId, "apple", ImmutableList.of(new VersionInfo("1.0"), new VersionInfo("2.0")));
        createMavenMetadata(targetRepo, groupId, "banana", ImmutableList.of(new VersionInfo("2.0-SNAPSHOT", "20110311.201909", "1")));
    }

    public static void createConfig(File dir, String groupId, String artifactId, String version, String timestampVersion)
            throws Exception
    {
        File configFile = newFile(dir,
                groupId,
                artifactId,
                version,
                artifactId + "-" + firstNonNull(timestampVersion, version) + ".config");

        configFile.getParentFile().mkdirs();

        ZipOutputStream out = new ZipOutputStream(new FileOutputStream(configFile));

        // text file
        out.putNextEntry(new ZipEntry(artifactId + ".txt"));
        out.write(artifactId.getBytes(UTF_8));

        // config.properties
        out.putNextEntry(new ZipEntry("config.properties"));
        String properties = "http-server.http.port=0\n" +
                "config=" + artifactId;
        out.write(properties.getBytes(UTF_8));

        // jvm.config
        out.putNextEntry(new ZipEntry("jvm.config"));

        // config.properties
        String resources;
        if ("apple".equals(artifactId)) {
            resources = "memory=512\n" +
                    "cpu=1";
        }
        else {
            resources = "memory=1024\n" +
                    "cpu=2";
        }
        out.putNextEntry(new ZipEntry("airship-resources.properties"));
        out.write(resources.getBytes(UTF_8));

        out.close();
    }

    private static void createMavenMetadata(File dir, String groupId, String artifactId, Iterable<VersionInfo> versions)
            throws Exception
    {
        MavenMetadata mavenMetadata = new MavenMetadata();
        mavenMetadata.groupId = groupId;
        mavenMetadata.artifactId = artifactId;
        mavenMetadata.versioning = new Versioning();
        for (VersionInfo version : versions) {
            mavenMetadata.versioning.versions.add(version.version);
        }
        MavenMetadata.marshalMavenMetadata(newFile(dir, groupId, artifactId, "maven-metadata.xml"), mavenMetadata);

        for (VersionInfo version : versions) {
            if (version.version.contains("-SNAPSHOT")) {
                MavenMetadata snapshotMetadata = new MavenMetadata();
                snapshotMetadata.groupId = groupId;
                snapshotMetadata.artifactId = artifactId;
                snapshotMetadata.version = version.version;

                snapshotMetadata.versioning = new Versioning();
                snapshotMetadata.versioning.snapshot = new Snapshot();
                snapshotMetadata.versioning.snapshot.timestamp = version.timestamp;
                snapshotMetadata.versioning.snapshot.buildNumber = version.buildNumber;

                SnapshotVersion snapshotVersion = new SnapshotVersion();
                snapshotVersion.extension = "config";
                snapshotVersion.value = String.format("%s-%s-%s",
                        version.version.replaceAll("-SNAPSHOT", ""),
                        version.timestamp,
                        version.buildNumber);
                snapshotMetadata.versioning.snapshotVersions.add(snapshotVersion);

                MavenMetadata.marshalMavenMetadata(newFile(dir, groupId, artifactId, version.version, "maven-metadata.xml"), snapshotMetadata);
            }
        }
    }

    private static class VersionInfo
    {
        public final String version;
        public final String timestamp;
        public final String buildNumber;

        private VersionInfo(String version)
        {
            this(version, null, null);
        }

        private VersionInfo(String version, String timestamp, String buildNumber)
        {
            this.version = version;
            this.timestamp = timestamp;
            this.buildNumber = buildNumber;
        }
    }
}
