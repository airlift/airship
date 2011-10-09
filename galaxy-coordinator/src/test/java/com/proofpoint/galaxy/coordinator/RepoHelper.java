package com.proofpoint.galaxy.coordinator;

import com.google.common.collect.ImmutableMap;
import com.google.common.io.InputSupplier;
import com.proofpoint.galaxy.shared.BinarySpec;
import com.proofpoint.galaxy.shared.ConfigRepository;
import com.proofpoint.galaxy.shared.ConfigSpec;

import java.io.InputStream;
import java.net.URI;
import java.util.Map;

public class RepoHelper
{
    public static final BinaryRepository MOCK_BINARY_REPO = new BinaryRepository()
    {
        @Override
        public URI getBinaryUri(BinarySpec binarySpec)
        {
            return URI.create("fake://localhost/" + binarySpec);
        }
    };
    public static final ConfigRepository MOCK_CONFIG_REPO = new ConfigRepository()
    {
        @Override
        public Map<String, URI> getConfigMap(String environment, ConfigSpec configSpec)
        {
            return ImmutableMap.of("config", URI.create("fake://localhost/" + configSpec));
        }

        @Override
        public InputSupplier<? extends InputStream> getConfigFile(String environment, ConfigSpec configSpec, String path)
        {
            return null;
        }
    };
}
