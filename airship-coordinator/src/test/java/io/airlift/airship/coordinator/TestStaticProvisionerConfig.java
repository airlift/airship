package io.airlift.airship.coordinator;

import com.google.common.collect.ImmutableMap;
import io.airlift.configuration.testing.ConfigAssertions;
import org.testng.annotations.Test;

import java.net.URI;
import java.util.Map;

public class TestStaticProvisionerConfig
{
    @Test
    public void testDefaults()
    {
        ConfigAssertions.assertRecordedDefaults(ConfigAssertions.recordDefaults(StaticProvisionerConfig.class)
                        .setCoordinatorsUri(URI.create("file:etc/coordinators.txt"))
                        .setAgentsUri(URI.create("file:etc/agents.txt"))
        );
    }

    @Test
    public void testExplicitPropertyMappings()
    {
        Map<String, String> properties = new ImmutableMap.Builder<String, String>()
                .put("coordinator.coordinators-uri", "file:/tmp/coordinators.txt")
                .put("coordinator.agents-uri", "file:/tmp/agents.txt")
                .build();

        StaticProvisionerConfig expected = new StaticProvisionerConfig()
                .setCoordinatorsUri(URI.create("file:/tmp/coordinators.txt"))
                .setAgentsUri(URI.create("file:/tmp/agents.txt"));

        ConfigAssertions.assertFullMapping(properties, expected);
    }
}
