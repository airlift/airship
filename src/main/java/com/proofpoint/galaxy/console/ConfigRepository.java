package com.proofpoint.galaxy.console;

import com.proofpoint.galaxy.ConfigSpec;

import java.net.URI;
import java.util.Map;

public interface ConfigRepository
{
    Map<String, URI> getConfigMap(ConfigSpec configSpec);
}
