package com.proofpoint.galaxy.console;

import com.google.common.collect.ImmutableMap;
import com.proofpoint.galaxy.ConfigSpec;

import java.net.URI;
import java.util.Map;

public class MockConfigRepository implements ConfigRepository
{
    public Map<String, URI> getConfigMap(ConfigSpec configSpec)
    {
        return ImmutableMap.of("config", URI.create("fake://localhost/" + configSpec));
    }
}
