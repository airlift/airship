package com.proofpoint.galaxy.coordinator;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.proofpoint.configuration.testing.ConfigAssertions;
import org.testng.annotations.Test;

import java.util.Map;

public class TestLocalProvisionerConfig
{
    @Test
    public void testDefaults()
    {
        ConfigAssertions.assertRecordedDefaults(ConfigAssertions.recordDefaults(LocalProvisionerConfig.class)
                .setLocalAgentUris("")
                .setExpectedStateDir("expected-state")
                .setAuthorizedKeysDir("authorized-keys")
        );
    }

    @Test
    public void testExplicitPropertyMappings()
    {
        Map<String, String> properties = new ImmutableMap.Builder<String, String>()
                .put("coordinator.agent-uri", "agent1,agent2,agent3")
                .put("coordinator.expected-state.dir", "state")
                .put("coordinator.auth.authorized-keys-dir", "keys")
                .build();

        LocalProvisionerConfig expected = new LocalProvisionerConfig()
                .setLocalAgentUris(ImmutableList.of("agent1", "agent2", "agent3"))
                .setExpectedStateDir("state")
                .setAuthorizedKeysDir("keys");

        ConfigAssertions.assertFullMapping(properties, expected);
    }

}
