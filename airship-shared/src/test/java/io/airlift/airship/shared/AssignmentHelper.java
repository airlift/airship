package io.airlift.airship.shared;

public class AssignmentHelper
{
    public static final Assignment APPLE_ASSIGNMENT = new Assignment("food.fruit:apple:1.0", "@prod:apple:1.0");
    public static final Assignment APPLE_ASSIGNMENT_2 = new Assignment("food.fruit:apple:2.0", "@prod:apple:2.0");
    public static final Assignment SHORT_APPLE_ASSIGNMENT = new Assignment("apple:1.0", "@apple:1.0");
    public static final Assignment RESOLVED_APPLE_ASSIGNMENT = new Assignment("food.fruit:apple:1.0", "@prod:apple:1.0");
    public static final Assignment BANANA_ASSIGNMENT = new Assignment("food.fruit:banana:2.0-SNAPSHOT", "@prod:banana:2.0-SNAPSHOT");
    public static final Assignment BANANA_ASSIGNMENT_EXACT = new Assignment("food.fruit:banana:2.0-20110311.201909-1", "@prod:banana:2.0-20110311.201909-1");
}
