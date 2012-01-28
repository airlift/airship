package com.proofpoint.galaxy.shared;

import static com.proofpoint.galaxy.shared.BinarySpec.createBinarySpec;
import static com.proofpoint.galaxy.shared.ConfigSpec.createConfigSpec;

public class AssignmentHelper
{
    public static final Assignment APPLE_ASSIGNMENT = new Assignment("food.fruit:apple:1.0", "@apple:1.0");
    public static final Assignment SHORT_APPLE_ASSIGNMENT = new Assignment("apple:1.0", "@apple:1.0");
    public static final Assignment RESOLVED_APPLE_ASSIGNMENT = new Assignment(
            createBinarySpec("food.fruit", "apple", "1.0", null, null, "1.0"),
            createConfigSpec("apple", "1.0"));
    public static final Assignment BANANA_ASSIGNMENT = new Assignment("food.fruit:banana:2.0-SNAPSHOT", "@banana:2.0-SNAPSHOT");
}
