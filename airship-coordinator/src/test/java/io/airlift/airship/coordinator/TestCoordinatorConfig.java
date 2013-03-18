package io.airlift.airship.coordinator;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import io.airlift.configuration.testing.ConfigAssertions;
import io.airlift.units.Duration;
import org.testng.annotations.Test;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import static io.airlift.airship.coordinator.CoordinatorConfig.DEFAULT_HTTP_SHORT_NAME_PATTERN;

public class TestCoordinatorConfig
{
    @Test
    public void testDefaults()
    {
        ConfigAssertions.assertRecordedDefaults(ConfigAssertions.recordDefaults(CoordinatorConfig.class)
                .setStatusExpiration(new Duration(5, TimeUnit.SECONDS))
                .setServiceInventoryCacheDir("service-inventory-cache")
                .setAllowDuplicateInstallationsOnAnAgent(false)
                .setRepositories("")
                .setDefaultRepositoryGroupId("")
                .setHttpShortNamePattern(DEFAULT_HTTP_SHORT_NAME_PATTERN)
                .setHttpRepoBinaryVersionPattern(null)
                .setHttpRepoConfigVersionPattern(null)
        );
    }

    @Test
    public void testExplicitPropertyMappings()
    {
        Map<String, String> properties = new ImmutableMap.Builder<String, String>()
                .put("coordinator.status.expiration", "1m")
                .put("coordinator.service-inventory.cache-dir", "si-cache")
                .put("coordinator.allow-duplicate-installations-on-an-agent", "true")
                .put("coordinator.repository", "repo1,repo2,repo3")
                .put("coordinator.default-group-id", "group1,group2,group3")
                .put("coordinator.http-repo.short-name-pattern", "shortNamePattern")
                .put("coordinator.http-repo.binary-version-pattern", "binaryVersionPattern")
                .put("coordinator.http-repo.config-version-pattern", "configVersionPattern")
                .build();

        CoordinatorConfig expected = new CoordinatorConfig()
                .setStatusExpiration(new Duration(1, TimeUnit.MINUTES))
                .setServiceInventoryCacheDir("si-cache")
                .setAllowDuplicateInstallationsOnAnAgent(true)
                .setRepositories(ImmutableList.of("repo1", "repo2", "repo3"))
                .setDefaultRepositoryGroupId(ImmutableList.of("group1", "group2", "group3"))
                .setHttpShortNamePattern("shortNamePattern")
                .setHttpRepoBinaryVersionPattern("binaryVersionPattern")
                .setHttpRepoConfigVersionPattern("configVersionPattern");

        ConfigAssertions.assertFullMapping(properties, expected);
    }

}
