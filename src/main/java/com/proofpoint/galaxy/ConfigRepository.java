package com.proofpoint.galaxy;

import java.net.URI;
import java.util.Map;

public interface ConfigRepository
{
    Map<String, URI> getConfigMap(ConfigSpec configSpec);
}
