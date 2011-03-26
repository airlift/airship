package com.proofpoint.galaxy;

import com.google.common.collect.ImmutableMap;

import java.net.URI;
import java.util.Map;

class MockConfigRepository implements ConfigRepository
{
    public Map<String, URI> getConfigMap(ConfigSpec configSpec)
    {
        return ImmutableMap.of("config", URI.create("fake://localhost/" + configSpec));
    }
}
