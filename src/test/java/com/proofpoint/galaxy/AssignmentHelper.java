package com.proofpoint.galaxy;

import com.proofpoint.galaxy.agent.Assignment;
import com.proofpoint.galaxy.console.BinaryRepository;
import com.proofpoint.galaxy.console.ConfigRepository;
import com.proofpoint.galaxy.console.ConsoleAssignment;
import com.proofpoint.galaxy.console.MockBinaryRepository;
import com.proofpoint.galaxy.console.MockConfigRepository;
import com.proofpoint.galaxy.console.TestingBinaryRepository;
import com.proofpoint.galaxy.console.TestingConfigRepository;

public class AssignmentHelper
{
    private final TestingBinaryRepository binaryRepository;
    private final TestingConfigRepository configRepository;

    private final Assignment appleAssignment;
    private final Assignment bananaAssignment;

    public AssignmentHelper()
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

        appleAssignment = createAssignment("food.fruit:apple:1.0", binaryRepository, "@prod:apple:1.0", configRepository);
        bananaAssignment = createAssignment("food.fruit:banana:2.0-SNAPSHOT", binaryRepository, "@prod:banana:2.0-SNAPSHOT", configRepository);

    }

    public void destroy()
    {
        binaryRepository.destroy();
        configRepository.destroy();
    }

    public Assignment getAppleAssignment()
    {
        return appleAssignment;
    }

    public Assignment getBananaAssignment()
    {
        return bananaAssignment;
    }

    public static Assignment createAssignment(String binary, BinaryRepository binaryRepository, String config, ConfigRepository configRepository)
    {
        return createAssignment(BinarySpec.valueOf(binary), binaryRepository, ConfigSpec.valueOf(config), configRepository);
    }

    public static Assignment createAssignment(BinarySpec binary, BinaryRepository binaryRepository, ConfigSpec config, ConfigRepository configRepository)
    {
        return new Assignment(binary, binaryRepository.getBinaryUri(binary), config, configRepository.getConfigMap(config));
    }

    private static final MockBinaryRepository mockBinaryRepository = new MockBinaryRepository();
    private static final MockConfigRepository mockConfigRepository = new MockConfigRepository();

    public static final Assignment MOCK_APPLE_ASSIGNMENT = createMockAssignment("food.fruit:apple:1.0", "@prod:apple:1.0");
    public static final Assignment MOCK_BANANA_ASSIGNMENT = createMockAssignment("food.fruit:banana:2.0-SNAPSHOT", "@prod:banana:2.0-SNAPSHOT");

    public static Assignment createMockAssignment(String binary, String config)
    {
        return createAssignment(binary, mockBinaryRepository, config, mockConfigRepository);
    }

    public static Assignment createMockAssignment(BinarySpec binary, ConfigSpec config)
    {
        return createAssignment(binary, mockBinaryRepository, config, mockConfigRepository);
    }
}
