package io.airlift.airship.coordinator;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import io.airlift.configuration.testing.ConfigAssertions;
import org.testng.annotations.Test;

import java.util.Map;

public class TestFixedProvisionerConfig
{
    @Test
    public void testDefaults()
    {
        ConfigAssertions.assertRecordedDefaults(ConfigAssertions.recordDefaults(FixedProvisionerConfig.class)
                .setLocalAgentUris("")
        );
    }

    @Test
    public void testExplicitPropertyMappings()
    {
        Map<String, String> properties = new ImmutableMap.Builder<String, String>()
                .put("coordinator.agent-uri", "agent1,agent2,agent3")
                .build();

        FixedProvisionerConfig expected = new FixedProvisionerConfig()
                .setLocalAgentUris(ImmutableList.of("agent1", "agent2", "agent3"));

        ConfigAssertions.assertFullMapping(properties, expected);
    }

}
