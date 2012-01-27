package com.proofpoint.galaxy.coordinator;

import com.google.common.collect.ImmutableList;
import com.proofpoint.galaxy.coordinator.MavenMetadata.Snapshot;
import com.proofpoint.galaxy.coordinator.MavenMetadata.SnapshotVersion;
import com.proofpoint.galaxy.coordinator.MavenMetadata.Versioning;

import java.io.File;
import java.io.FileOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static com.google.common.base.Charsets.UTF_8;
import static com.google.common.base.Objects.firstNonNull;
import static com.proofpoint.galaxy.shared.FileUtils.createTempDir;
import static com.proofpoint.galaxy.shared.FileUtils.deleteRecursively;
import static com.proofpoint.galaxy.shared.FileUtils.newFile;

public class TestingConfigRepository extends ConfigInBinaryRepository
{
    private final File targetRepo;

    public TestingConfigRepository()
            throws Exception
    {
        this(createConfigRepoDir());
    }

    public TestingConfigRepository(File targetRepo)
            throws Exception
    {
        super(new MavenBinaryRepository(ImmutableList.<String>of("prod"), targetRepo.toURI()));
        this.targetRepo = targetRepo;
    }

    public void destroy()
    {
        deleteRecursively(targetRepo);
    }

    public static File createConfigRepoDir()
            throws Exception
    {
        File targetRepo = createTempDir("config");
        try {

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
                artifactId + "-"+ firstNonNull(timestampVersion, version) + ".config");

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
        out.putNextEntry(new ZipEntry("galaxy-resources.properties"));
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
