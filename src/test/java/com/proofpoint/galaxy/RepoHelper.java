package com.proofpoint.galaxy;

import com.google.common.collect.ImmutableMap;
import com.proofpoint.galaxy.agent.Installation;
import com.proofpoint.galaxy.console.BinaryRepository;
import com.proofpoint.galaxy.console.ConfigRepository;
import com.proofpoint.galaxy.console.TestingBinaryRepository;
import com.proofpoint.galaxy.console.TestingConfigRepository;

import java.net.URI;
import java.util.Map;

import static com.proofpoint.galaxy.AssignmentHelper.APPLE_ASSIGNMENT;
import static com.proofpoint.galaxy.AssignmentHelper.BANANA_ASSIGNMENT;

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

    private final TestingBinaryRepository binaryRepository;
    private final TestingConfigRepository configRepository;

    private final Installation appleInstallation;
    private final Installation bananaInstallation;

    public RepoHelper()
            throws Exception
    {
        binaryRepository = new TestingBinaryRepository();
        try {
            configRepository = new TestingConfigRepository();
        }
        catch (Exception e) {
            binaryRepository.destroy();
            throw e;
        }

        appleInstallation = new Installation(APPLE_ASSIGNMENT, binaryRepository, configRepository);
        bananaInstallation = new Installation(BANANA_ASSIGNMENT, binaryRepository, configRepository);
    }

    public void destroy()
    {
        binaryRepository.destroy();
        configRepository.destroy();
    }

    public Installation getAppleInstallation()
    {
        return appleInstallation;
    }

    public Installation getBananaInstallation()
    {
        return bananaInstallation;
    }
}
