package com.proofpoint.galaxy.coordinator;

import com.google.common.collect.ImmutableMap;
import com.proofpoint.galaxy.shared.BinarySpec;
import com.proofpoint.galaxy.shared.ConfigSpec;
import com.proofpoint.galaxy.shared.Installation;

import java.net.URI;
import java.util.Map;

import static com.proofpoint.galaxy.shared.AssignmentHelper.APPLE_ASSIGNMENT;
import static com.proofpoint.galaxy.shared.AssignmentHelper.BANANA_ASSIGNMENT;

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
        public Map<String, URI> getConfigMap(ConfigSpec configSpec)
        {
            return ImmutableMap.of("config", URI.create("fake://localhost/" + configSpec));
        }
    };
}
