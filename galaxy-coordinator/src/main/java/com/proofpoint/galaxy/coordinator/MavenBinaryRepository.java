package com.proofpoint.galaxy.coordinator;


import com.google.common.collect.ImmutableList;
import com.proofpoint.galaxy.shared.BinarySpec;
import com.proofpoint.log.Logger;
import org.apache.maven.repository.internal.DefaultServiceLocator;
import org.apache.maven.repository.internal.MavenRepositorySystemSession;
import org.apache.maven.settings.Settings;
import org.apache.maven.settings.building.DefaultSettingsBuilderFactory;
import org.apache.maven.settings.building.DefaultSettingsBuildingRequest;
import org.apache.maven.settings.building.SettingsBuilder;
import org.apache.maven.settings.building.SettingsBuildingException;
import org.apache.maven.settings.building.SettingsBuildingRequest;
import org.sonatype.aether.RepositorySystem;
import org.sonatype.aether.RepositorySystemSession;
import org.sonatype.aether.collection.CollectRequest;
import org.sonatype.aether.connector.async.AsyncRepositoryConnectorFactory;
import org.sonatype.aether.connector.file.FileRepositoryConnectorFactory;
import org.sonatype.aether.graph.Dependency;
import org.sonatype.aether.graph.DependencyNode;
import org.sonatype.aether.repository.LocalRepository;
import org.sonatype.aether.repository.RemoteRepository;
import org.sonatype.aether.repository.RepositoryPolicy;
import org.sonatype.aether.resolution.ArtifactRequest;
import org.sonatype.aether.resolution.ArtifactResolutionException;
import org.sonatype.aether.resolution.DependencyRequest;
import org.sonatype.aether.resolution.DependencyResolutionException;
import org.sonatype.aether.spi.connector.RepositoryConnectorFactory;
import org.sonatype.aether.util.artifact.DefaultArtifact;
import org.sonatype.aether.util.graph.PreorderNodeListGenerator;

import javax.inject.Inject;
import java.io.File;
import java.net.URI;
import java.util.List;

import static com.proofpoint.galaxy.shared.FileUtils.newFile;

public class MavenBinaryRepository implements BinaryRepository
{
    private static final Logger log = Logger.get(MavenBinaryRepository.class);
    private final List<RemoteRepository> remoteRepositories;
    private final RepositorySystem repositorySystem;
    private final RepositorySystemSession resolutionSession;

    @Inject
    public MavenBinaryRepository(CoordinatorConfig config)
    {
        this(config.getLocalBinaryRepo(), config.isLocalMavenRepositoryEnabled(), config.getBinaryRepoBases());
    }

    public MavenBinaryRepository(String localRepository, boolean userLocalRepositoryEnabled, String... binaryRepoBases)
    {
        this(localRepository, userLocalRepositoryEnabled, ImmutableList.copyOf(binaryRepoBases));
    }

    public MavenBinaryRepository(String localRepository, boolean userLocalRepositoryEnabled, Iterable<String> binaryRepoBases)
    {
        if (localRepository == null || localRepository.trim().isEmpty()) {
            Settings settings = readMavenSettings();
            localRepository = settings.getLocalRepository();
            if (localRepository == null || localRepository.trim().isEmpty()) {
                if (userLocalRepositoryEnabled) {
                    localRepository = newFile(System.getProperty("user.home"), ".m2", "repository").getAbsolutePath();
                }
                else {
                    localRepository = newFile(".m2", "repository").getAbsolutePath();
                }
            }
        }

        repositorySystem = new DefaultServiceLocator()
                .addService(RepositoryConnectorFactory.class, AsyncRepositoryConnectorFactory.class)
                .addService(RepositoryConnectorFactory.class, FileRepositoryConnectorFactory.class)
                .getService(RepositorySystem.class);

        long repositoryId = 1;
        ImmutableList.Builder<RemoteRepository> repositories = ImmutableList.builder();
        for (String binaryRepositoryBase : binaryRepoBases) {
            RemoteRepository remoteRepository = new RemoteRepository("repository" + repositoryId++, "default", binaryRepositoryBase);
            repositories.add(remoteRepository);
        }
        remoteRepositories = repositories.build();

        resolutionSession = new MavenRepositorySystemSession()
                .setLocalRepositoryManager(repositorySystem.newLocalRepositoryManager(new LocalRepository(localRepository)))
                .setTransferListener(new AetherTransferListener())
                .setUpdatePolicy(RepositoryPolicy.UPDATE_POLICY_ALWAYS);
    }

    @Override
    public URI getBinaryUri(BinarySpec binarySpec)
    {
        try {
            return resolve(binarySpec.toGAV("jar")).toURI();
        }
        catch (ArtifactResolutionException e) {
            return null;
        }
    }

    public File resolve(String coordinates)
            throws ArtifactResolutionException
    {
        DefaultArtifact artifact = new DefaultArtifact(coordinates);

        ArtifactRequest request = new ArtifactRequest();
        request.setArtifact(artifact);
        for (RemoteRepository remoteRepository : remoteRepositories) {
            request.addRepository(remoteRepository);
        }
        return repositorySystem.resolveArtifact(resolutionSession, request).getArtifact().getFile();
    }

    /**
     * This resolves the root dependency and all its transitive dependencies
     */
    public ImmutableList<File> resolveTransitive(String coordinates)
            throws DependencyResolutionException
    {
        Dependency dependency = new Dependency(new DefaultArtifact(coordinates), "runtime");

        CollectRequest request = new CollectRequest();
        request.setRoot(dependency);
        for (RemoteRepository remoteRepository : remoteRepositories) {
            request.addRepository(remoteRepository);
        }

        DependencyRequest dependencyRequest = new DependencyRequest();
        dependencyRequest.setCollectRequest(request);

        DependencyNode rootNode = repositorySystem.resolveDependencies(resolutionSession, dependencyRequest).getRoot();
        PreorderNodeListGenerator preorderNodeListGenerator = new PreorderNodeListGenerator();
        rootNode.accept(preorderNodeListGenerator);
        return ImmutableList.copyOf(preorderNodeListGenerator.getFiles());
    }

    private static Settings readMavenSettings()
    {
        SettingsBuilder settingsBuilder = new DefaultSettingsBuilderFactory().newInstance();

        SettingsBuildingRequest request = new DefaultSettingsBuildingRequest();
        request.setSystemProperties(System.getProperties());
        request.setUserSettingsFile(new File(System.getProperty("user.home"), ".m2/settings.xml"));

        Settings settings;
        try {
            settings = settingsBuilder.build(request).getEffectiveSettings();
        }
        catch (SettingsBuildingException e) {
            settings = new Settings();
        }

        return settings;
    }
}
