package com.proofpoint.galaxy.configbundler;

import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import org.apache.maven.settings.Profile;
import org.apache.maven.settings.Repository;
import org.apache.maven.settings.Server;
import org.apache.maven.settings.Settings;
import org.apache.maven.settings.building.DefaultSettingsBuilderFactory;
import org.apache.maven.settings.building.DefaultSettingsBuildingRequest;
import org.apache.maven.settings.building.SettingsBuildingException;
import org.apache.maven.settings.building.SettingsBuildingRequest;

import java.io.File;
import java.net.URI;
import java.util.List;
import java.util.Set;

import static com.google.common.collect.Iterables.find;

public class Maven
{
    private static final String USER_DIR = System.getProperty("user.dir", "");
    private static final String USER_HOME = System.getProperty("user.home");
    private static final String MAVEN_HOME = System.getProperty("maven.home", USER_DIR);
    private static final File MAVEN_USER_HOME = new File(USER_HOME, ".m2");
    private static final File DEFAULT_USER_SETTINGS_FILE = new File(MAVEN_USER_HOME, "settings.xml");
    private static final File DEFAULT_GLOBAL_SETTINGS_FILE = new File(MAVEN_HOME, "conf/settings.xml");

    private final Settings settings;

    public Maven()
            throws SettingsBuildingException
    {
        final SettingsBuildingRequest request = new DefaultSettingsBuildingRequest()
                .setGlobalSettingsFile(DEFAULT_GLOBAL_SETTINGS_FILE)
                .setUserSettingsFile(DEFAULT_USER_SETTINGS_FILE)
                .setSystemProperties(System.getProperties());

        settings = new DefaultSettingsBuilderFactory()
                .newInstance()
                .build(request)
                .getEffectiveSettings();
    }

    public MavenUploader getUploader(String repositoryId)
    {
        Repository repository = find(getActiveRepositories(), matchesId(repositoryId), null);
        Preconditions.checkArgument(repository != null, "Repository '%s' not found", repositoryId);

        Server server = settings.getServer(repository.getId());
        Preconditions.checkArgument(server != null && server.getPassword() != null, "No credentials found for repository '%s'", repositoryId);

        return new MavenUploader(URI.create(repository.getUrl()), server.getUsername(), server.getPassword());
    }

    private List<Repository> getActiveRepositories()
    {
        ImmutableList.Builder<Repository> builder = ImmutableList.builder();

        final Set<String> activeProfiles = ImmutableSet.copyOf(settings.getActiveProfiles());
        for (Profile profile : settings.getProfiles()) {
            if (activeProfiles.contains(profile.getId())) {
                builder.addAll(profile.getRepositories());
            }
        }

        return builder.build();
    }

    private static Predicate<? super Repository> matchesId(final String repositoryId)
    {
        return new Predicate<Repository>()
        {
            public boolean apply(Repository input)
            {
                return input.getId().equals(repositoryId);
            }
        };
    }
}
