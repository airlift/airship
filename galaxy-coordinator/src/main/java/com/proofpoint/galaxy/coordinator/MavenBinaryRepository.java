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
import com.proofpoint.http.server.HttpServerInfo;
import org.apache.maven.settings.Profile;
import org.apache.maven.settings.Repository;
import org.apache.maven.settings.Settings;
import org.apache.maven.settings.building.DefaultSettingsBuilder;
import org.apache.maven.settings.building.DefaultSettingsBuilderFactory;
import org.apache.maven.settings.building.DefaultSettingsBuildingRequest;
import org.apache.maven.settings.building.SettingsBuildingResult;

import java.io.File;
import java.io.InputStream;
import java.net.URI;
import java.util.List;

import static com.proofpoint.galaxy.shared.FileUtils.newFile;

public class MavenBinaryRepository implements BinaryRepository
{
    private final List<URI> binaryRepositoryBases;
    private final URI localMavenRepo;
    private final URI localBinaryUri;

    public MavenBinaryRepository(URI binaryRepositoryBase, URI... binaryRepositoryBases)
    {
        this.binaryRepositoryBases = ImmutableList.<URI>builder().add(binaryRepositoryBase).add(binaryRepositoryBases).build();
        localMavenRepo = null;
        localBinaryUri = null;
    }

    public MavenBinaryRepository(Iterable<URI> binaryRepositoryBases)
    {
        this.binaryRepositoryBases = ImmutableList.copyOf(binaryRepositoryBases);
        localMavenRepo = null;
        localBinaryUri = null;
    }

    @Inject
    public MavenBinaryRepository(CoordinatorConfig config, HttpServerInfo httpServerInfo)
            throws Exception
    {
        Builder<URI> builder = ImmutableList.builder();
        if (config.isLocalMavenRepositoryEnabled()) {
            // add the local maven repository first
            File localMavenRepo = newFile(System.getProperty("user.home"), ".m2", "repository");
            if (localMavenRepo.isDirectory()) {
                this.localMavenRepo = localMavenRepo.toURI();
            } else {
                this.localMavenRepo = null;
            }


            // add all automatically activated repositories in the settings.xml file
            File settingsFile = newFile(System.getProperty("user.home"), ".m2", "settings.xml");
            if (settingsFile.canRead()) {
                DefaultSettingsBuilder settingsBuilder = new DefaultSettingsBuilderFactory().newInstance();
                SettingsBuildingResult settingsBuildingResult = settingsBuilder.build(new DefaultSettingsBuildingRequest()
                        .setUserSettingsFile(settingsFile)
                );
                Settings settings = settingsBuildingResult.getEffectiveSettings();
                for (Profile profile : settings.getProfiles()) {
                    if (isProfileActive(settings, profile)) {
                        for (Repository repository : profile.getRepositories()) {
                            String url = repository.getUrl();
                            if (!url.endsWith("/")) {
                                url = url + "/";
                            }
                            builder.add(URI.create(url));
                        }
                    }
                }
            }

        } else {
            localMavenRepo = null;
        }

        if (httpServerInfo.getHttpsUri() != null) {
            localBinaryUri = httpServerInfo.getHttpsUri().resolve("/v1/binary/");
        }
        else {
            localBinaryUri = httpServerInfo.getHttpUri().resolve("/v1/binary/");
        }

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
        if (localMavenRepo != null) {
            URI uri = getBinaryUri(binarySpec, localMavenRepo);
            checkedUris.add(uri);
            if (isValidBinary(uri)) {
                String binaryPath;
                if (binarySpec.getClassifier() != null) {
                    binaryPath = String.format("%s/%s/%s/%s/%s", binarySpec.getGroupId(), binarySpec.getArtifactId(), binarySpec.getVersion(), binarySpec.getPackaging(), binarySpec.getClassifier());
                }
                else {
                    binaryPath = String.format("%s/%s/%s/%s", binarySpec.getGroupId(), binarySpec.getArtifactId(), binarySpec.getVersion(), binarySpec.getPackaging());
                }
                return localBinaryUri.resolve(binaryPath);
            }
        }

        for (URI binaryRepositoryBase : binaryRepositoryBases) {
            URI uri = getBinaryUri(binarySpec, binaryRepositoryBase);
            checkedUris.add(uri);
            if (isValidBinary(uri)) {
                return uri;
            }
        }
        throw new RuntimeException("Unable to find binary " + binarySpec + " at " + checkedUris);
    }

    public static URI getBinaryUri(BinarySpec binarySpec, URI binaryRepositoryBase)
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
        return uri;
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

    private static boolean isProfileActive(Settings settings, Profile profile)
    {
        if ((profile.getActivation() != null) && profile.getActivation().isActiveByDefault()) {
            return true;
        }
        for (String profileId : settings.getActiveProfiles()) {
            if (profileId.equals(profile.getId())) {
                return true;
            }
        }
        return false;
    }
}
