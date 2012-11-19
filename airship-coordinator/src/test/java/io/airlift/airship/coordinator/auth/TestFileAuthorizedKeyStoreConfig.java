package io.airlift.airship.coordinator.auth;

import com.google.common.collect.ImmutableMap;
import io.airlift.configuration.testing.ConfigAssertions;
import org.testng.annotations.Test;

import java.util.Map;

public class TestFileAuthorizedKeyStoreConfig
{
    @Test
    public void testDefaults()
    {
        ConfigAssertions.assertRecordedDefaults(ConfigAssertions.recordDefaults(FileAuthorizedKeyStoreConfig.class)
                .setAuthorizedKeysDir("authorized-keys")
        );
    }

    @Test
    public void testExplicitPropertyMappings()
    {
        Map<String, String> properties = new ImmutableMap.Builder<String, String>()
                .put("coordinator.auth.authorized-keys-dir", "keys")
                .build();

        FileAuthorizedKeyStoreConfig expected = new FileAuthorizedKeyStoreConfig()
                .setAuthorizedKeysDir("keys");

        ConfigAssertions.assertFullMapping(properties, expected);
    }

}
