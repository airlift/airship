package com.proofpoint.galaxy.shared;

import com.google.common.io.InputSupplier;

import java.io.InputStream;
import java.net.URI;
import java.util.Map;

public interface ConfigRepository
{
    Map<String, URI> getConfigMap(String environment, ConfigSpec configSpec);
    InputSupplier<? extends InputStream> getConfigFile(String environment, ConfigSpec configSpec, String path);
}
