package io.airlift.airship.coordinator;

import com.google.common.collect.ImmutableMap;
import io.airlift.configuration.testing.ConfigAssertions;
import org.testng.annotations.Test;

import java.util.Map;

public class TestFileStateManagerConfig
{
    @Test
    public void testDefaults()
    {
        ConfigAssertions.assertRecordedDefaults(ConfigAssertions.recordDefaults(FileStateManagerConfig.class)
                        .setExpectedStateDir("expected-state")
        );
    }

    @Test
    public void testExplicitPropertyMappings()
    {
        Map<String, String> properties = new ImmutableMap.Builder<String, String>()
                .put("coordinator.expected-state.dir", "state")
                .build();

        FileStateManagerConfig expected = new FileStateManagerConfig()
                .setExpectedStateDir("state");

        ConfigAssertions.assertFullMapping(properties, expected);
    }
}
