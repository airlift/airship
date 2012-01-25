package com.proofpoint.galaxy.coordinator;

import com.proofpoint.galaxy.shared.BinarySpec;
import com.proofpoint.galaxy.shared.ConfigRepository;
import com.proofpoint.galaxy.shared.ConfigSpec;

import java.net.URI;

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
        public URI getConfigFile(ConfigSpec configSpec)
        {
            return URI.create("fake://localhost/" + configSpec);

        }
    };
}
