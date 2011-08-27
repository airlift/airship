package com.proofpoint.galaxy.configuration;

import com.google.common.io.InputSupplier;
import com.proofpoint.galaxy.shared.ConfigSpec;

import java.io.InputStream;
import java.net.URI;
import java.util.Map;

public interface ConfigurationRepository
{
    Map<String, URI> getConfigMap(ConfigSpec configSpec);
    InputSupplier<? extends InputStream> getConfigFile(ConfigSpec configSpec, String path);
}
