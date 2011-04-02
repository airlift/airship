package com.proofpoint.galaxy.coordinator;

import com.proofpoint.galaxy.shared.ConfigSpec;

import java.net.URI;
import java.util.Map;

public interface ConfigRepository
{
    Map<String, URI> getConfigMap(ConfigSpec configSpec);
}
