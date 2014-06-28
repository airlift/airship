package io.airlift.airship.configbundler;

import com.google.common.collect.ImmutableList;
import com.google.common.io.ByteSource;
import org.apache.maven.repository.internal.DefaultArtifactDescriptorReader;
import org.apache.maven.repository.internal.DefaultVersionRangeResolver;
import org.apache.maven.repository.internal.DefaultVersionResolver;
import org.apache.maven.repository.internal.MavenRepositorySystemSession;
import org.apache.maven.repository.internal.SnapshotMetadataGeneratorFactory;
import org.apache.maven.settings.Server;
import org.apache.maven.settings.Settings;
import org.apache.maven.settings.building.DefaultSettingsBuilderFactory;
import org.apache.maven.settings.building.DefaultSettingsBuildingRequest;
import org.apache.maven.settings.building.SettingsBuildingException;
import org.apache.maven.settings.building.SettingsBuildingRequest;
import org.sonatype.aether.RepositorySystem;
import org.sonatype.aether.RepositorySystemSession;
import org.sonatype.aether.artifact.Artifact;
import org.sonatype.aether.connector.async.AsyncRepositoryConnectorFactory;
import org.sonatype.aether.connector.file.FileRepositoryConnectorFactory;
import org.sonatype.aether.deployment.DeployRequest;
import org.sonatype.aether.impl.ArtifactDescriptorReader;
import org.sonatype.aether.impl.MetadataGeneratorFactory;
import org.sonatype.aether.impl.VersionRangeResolver;
import org.sonatype.aether.impl.VersionResolver;
import org.sonatype.aether.impl.internal.DefaultServiceLocator;
import org.sonatype.aether.installation.InstallRequest;
import org.sonatype.aether.repository.Authentication;
import org.sonatype.aether.repository.LocalRepository;
import org.sonatype.aether.repository.RemoteRepository;
import org.sonatype.aether.repository.RepositoryPolicy;
import org.sonatype.aether.resolution.ArtifactRequest;
import org.sonatype.aether.resolution.ArtifactResolutionException;
import org.sonatype.aether.spi.connector.RepositoryConnectorFactory;
import org.sonatype.aether.util.artifact.DefaultArtifact;

import javax.annotation.Nullable;

import java.io.File;
import java.io.FileOutputStream;

import static com.google.common.base.Preconditions.checkNotNull;

class Maven
{
    private static final String USER_DIR = System.getProperty("user.dir", "");
    private static final String USER_HOME = System.getProperty("user.home");
    private static final String MAVEN_HOME = System.getProperty("maven.home", USER_DIR);
    private static final File MAVEN_USER_HOME = new File(USER_HOME, ".m2");
    private static final File DEFAULT_USER_SETTINGS_FILE = new File(MAVEN_USER_HOME, "settings.xml");
    private static final File DEFAULT_GLOBAL_SETTINGS_FILE = new File(MAVEN_HOME, "conf/settings.xml");

    private final RepositorySystem repositorySystem;
    private final RepositorySystemSession session;

    private final RemoteRepository snapshotsRepository;
    private final RemoteRepository releasesRepository;

    public Maven(@Nullable Metadata.Repository snapshotsRepositoryInfo,
            @Nullable Metadata.Repository releasesRepositoryInfo)
            throws SettingsBuildingException
    {
        validateRepositoryMetadata(snapshotsRepositoryInfo, "snapshots");
        validateRepositoryMetadata(releasesRepositoryInfo, "releases");

        final SettingsBuildingRequest request = new DefaultSettingsBuildingRequest()
                .setGlobalSettingsFile(DEFAULT_GLOBAL_SETTINGS_FILE)
                .setUserSettingsFile(DEFAULT_USER_SETTINGS_FILE)
                .setSystemProperties(System.getProperties());

        Settings settings = new DefaultSettingsBuilderFactory()
                .newInstance()
                .build(request)
                .getEffectiveSettings();

        repositorySystem = new DefaultServiceLocator()
                .addService(RepositoryConnectorFactory.class, AsyncRepositoryConnectorFactory.class)
                .addService(RepositoryConnectorFactory.class, FileRepositoryConnectorFactory.class)
                .addService(VersionResolver.class, DefaultVersionResolver.class)
                .addService(VersionRangeResolver.class, DefaultVersionRangeResolver.class)
                .addService(ArtifactDescriptorReader.class, DefaultArtifactDescriptorReader.class)
                .addService(MetadataGeneratorFactory.class, SnapshotMetadataGeneratorFactory.class)
                .getService(RepositorySystem.class);

        String localRepository = settings.getLocalRepository();
        if (localRepository == null || localRepository.trim().isEmpty()) {
            localRepository = new File(MAVEN_USER_HOME, "repository").getAbsolutePath();
        }

        session = new MavenRepositorySystemSession()
                .setLocalRepositoryManager(repositorySystem.newLocalRepositoryManager(new LocalRepository(localRepository)));

        releasesRepository = makeRemoteRepository(releasesRepositoryInfo, settings, false);
        snapshotsRepository = makeRemoteRepository(snapshotsRepositoryInfo, settings, true);
    }

    private static void validateRepositoryMetadata(@Nullable Metadata.Repository info, String name)
    {
        if (info != null) {
            checkNotNull(info.getId(), "%s repository id is null", name);
            checkNotNull(info.getUri(), "%s repository uri is null", name);
        }
    }

    private static RemoteRepository makeRemoteRepository(@Nullable Metadata.Repository info, Settings settings, boolean snapshot)
    {
        if (info == null) {
            return null;
        }

        Server server = settings.getServer(info.getId());
        return new RemoteRepository(info.getId(), "default", info.getUri())
                .setPolicy(true, new RepositoryPolicy(snapshot, RepositoryPolicy.UPDATE_POLICY_ALWAYS, RepositoryPolicy.CHECKSUM_POLICY_FAIL))
                .setPolicy(false, new RepositoryPolicy(!snapshot, RepositoryPolicy.UPDATE_POLICY_ALWAYS, RepositoryPolicy.CHECKSUM_POLICY_FAIL))
                .setAuthentication(new Authentication(server.getUsername(), server.getPassword()));
    }

    public boolean upload(String groupId, String artifactId, String version, String extension, ByteSource source)
            throws Exception
    {
        File file = File.createTempFile(String.format("%s-%s-%s", groupId, artifactId, version), "." + extension);
        try {
            FileOutputStream out = new FileOutputStream(file);
            try {
                source.copyTo(out);
            }
            finally {
                out.close();
            }

            Artifact artifact = new DefaultArtifact(groupId, artifactId, extension, version).setFile(file);

            // Install the artifact locally
            repositorySystem.install(session, new InstallRequest().addArtifact(artifact));

            // Deploy the artifact remotely if a repository is configured.

            RemoteRepository repository = artifact.isSnapshot() ? snapshotsRepository : releasesRepository;

            if (repository != null) {
                DeployRequest deployRequest = new DeployRequest()
                        .addArtifact(artifact)
                        .setRepository(repository);

                repositorySystem.deploy(session, deployRequest);
                return true; // Uploaded successfully.
            }
            else {
                return false; // Only installed, not uploaded.
            }
        }
        finally {
            file.delete();
        }
    }

    public boolean contains(String groupId, String artifactId, String version, String type)
    {
        Artifact artifact = new DefaultArtifact(groupId, artifactId, type, version);

        try {
            repositorySystem.resolveArtifact(session, new ArtifactRequest(artifact, ImmutableList.of(snapshotsRepository, releasesRepository), null));
        }
        catch (ArtifactResolutionException e) {
            return false;
        }

        return true;
    }
}
