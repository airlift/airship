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
            throws Exception
    {
        Builder<URI> builder = ImmutableList.builder();
        if (config.isLocalMavenRepositoryEnabled()) {
            // add the local maven repository first
            File localMavenRepo = newFile(System.getProperty("user.home"), ".m2", "repository");
            if (localMavenRepo.isDirectory()) {
                builder.add(localMavenRepo.toURI());
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
                    if (profile.getActivation() != null && profile.getActivation().isActiveByDefault()) {
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
