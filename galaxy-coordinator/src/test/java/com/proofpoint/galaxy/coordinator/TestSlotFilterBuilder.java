package com.proofpoint.galaxy.coordinator;

import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.proofpoint.galaxy.coordinator.SlotFilterBuilder.SlotUuidPredicate;
import com.proofpoint.galaxy.shared.Assignment;
import com.proofpoint.galaxy.shared.MockUriInfo;
import com.proofpoint.galaxy.shared.SlotStatus;
import com.proofpoint.galaxy.coordinator.SlotFilterBuilder.BinarySpecPredicate;
import com.proofpoint.galaxy.coordinator.SlotFilterBuilder.ConfigSpecPredicate;
import com.proofpoint.galaxy.coordinator.SlotFilterBuilder.HostPredicate;
import com.proofpoint.galaxy.coordinator.SlotFilterBuilder.StatePredicate;
import org.testng.annotations.Test;

import java.net.URI;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static com.proofpoint.galaxy.shared.AssignmentHelper.APPLE_ASSIGNMENT;
import static com.proofpoint.galaxy.shared.SlotLifecycleState.RUNNING;
import static com.proofpoint.galaxy.shared.SlotLifecycleState.UNKNOWN;
import static java.util.Arrays.asList;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

public class TestSlotFilterBuilder
{
    private final SlotStatus status = new SlotStatus(UUID.fromString("12345678-1234-1234-1234-123456789012"),
            "slotName",
            URI.create("fake://localhost"),
            URI.create("fake://localhost"),
            "location",
            UNKNOWN,
            APPLE_ASSIGNMENT,
            "/slotName",
            ImmutableMap.<String, Integer>of());


    private Predicate<SlotStatus> buildFilter(String key, String value, List<UUID> uuids)
    {
        return SlotFilterBuilder.build(MockUriInfo.from("fake://localhost?" + key + "=" + value), true, uuids);
    }

    private Predicate<SlotStatus> buildFilter(String key, String value)
    {
        return buildFilter(key, value, Collections.<UUID>emptyList());
    }

    @Test(expectedExceptions = InvalidSlotFilterException.class)
    public void testEmptyFilter()
    {
        SlotFilterBuilder.build(MockUriInfo.from("fake://localhost"), true, ImmutableList.<UUID>of()).apply(status);
    }

    @Test
    public void testAll()
    {
        assertTrue(SlotFilterBuilder.build(MockUriInfo.from("fake://localhost?all&state=unknown"), true, ImmutableList.<UUID>of()).apply(status));
        assertTrue(SlotFilterBuilder.build(MockUriInfo.from("fake://localhost?all&state=running"), true, ImmutableList.<UUID>of()).apply(status));
        assertTrue(SlotFilterBuilder.build(MockUriInfo.from("fake://localhost?all&host=host"), true, ImmutableList.<UUID>of()).apply(status));
    }

    @Test
    public void testStateSpecPredicate()
    {
        assertTrue(new StatePredicate(UNKNOWN).apply(status));
        assertTrue(buildFilter("state", "unknown").apply(status));
        assertTrue(buildFilter("state", "u").apply(status));
        assertTrue(buildFilter("state", "UnKnown").apply(status));
        assertTrue(buildFilter("state", "U").apply(status));
        assertFalse(new StatePredicate(RUNNING).apply(status));
        assertFalse(buildFilter("state", "running").apply(status));
        assertFalse(buildFilter("state", "r").apply(status));
    }

    @Test
    public void testSlotUuidPredicate()
    {
        assertTrue(new SlotUuidPredicate(UUID.fromString("12345678-1234-1234-1234-123456789012")).apply(status));
        assertTrue(buildFilter("uuid", "12345678-1234-1234-1234-123456789012", asList(UUID.fromString("12345678-1234-1234-1234-123456789012"))).apply(status));
        assertFalse(new SlotUuidPredicate(UUID.fromString("00000000-0000-0000-0000-000000000000")).apply(status));
        assertFalse(buildFilter("uuid", "00000000-0000-0000-0000-000000000000").apply(status));
    }

    @Test
    public void testHostSpecPredicate()
    {
        assertTrue(new HostPredicate("localhost").apply(status));
        assertTrue(buildFilter("host", "localhost").apply(status));
        assertTrue(new HostPredicate("LOCALHOST").apply(status));
        assertTrue(buildFilter("host", "LOCALHOST").apply(status));
        assertTrue(new HostPredicate("LocalHost").apply(status));
        assertTrue(buildFilter("host", "LocalHost").apply(status));
        assertTrue(new HostPredicate("local*").apply(status));
        assertTrue(buildFilter("host", "local*").apply(status));
        assertTrue(new HostPredicate("LocAL*").apply(status));
        assertTrue(buildFilter("host", "LocAL*").apply(status));
        assertFalse(new HostPredicate("foo").apply(status));
        assertFalse(buildFilter("host", "foo").apply(status));

        assertTrue(new HostPredicate("127.0.0.1").apply(status));
        assertTrue(buildFilter("host", "127.0.0.1").apply(status));
        assertFalse(new HostPredicate("10.1.2.3").apply(status));
        assertFalse(buildFilter("host", "10.1.2.3").apply(status));
    }

    @Test
    public void testBinarySpecPredicate()
    {
        assertTrue(new BinarySpecPredicate("*").apply(status));
        assertTrue(buildFilter("binary", "*").apply(status));

        assertTrue(new BinarySpecPredicate("food.fruit:apple:1.0").apply(status));
        assertTrue(buildFilter("binary", "food.fruit:apple:1.0").apply(status));

        assertTrue(new BinarySpecPredicate("*apple*").apply(status));
        assertTrue(buildFilter("binary", "*apple*").apply(status));

        assertTrue(new BinarySpecPredicate("*:apple:1.0").apply(status));
        assertTrue(buildFilter("binary", "*:apple:1.0").apply(status));

        assertTrue(new BinarySpecPredicate("food.fruit:*:1.0").apply(status));
        assertTrue(buildFilter("binary", "food.fruit:*:1.0").apply(status));

        assertTrue(new BinarySpecPredicate("food.fruit:apple:*").apply(status));
        assertTrue(buildFilter("binary", "food.fruit:apple:*").apply(status));

        assertTrue(new BinarySpecPredicate("f*:a*:1.*").apply(status));
        assertTrue(buildFilter("binary", "f*:a*:1.*").apply(status));

        assertFalse(new BinarySpecPredicate("*banana*").apply(status));
        assertFalse(buildFilter("binary", "*banana*").apply(status));

        assertFalse(new BinarySpecPredicate("x:apple:1.0").apply(status));
        assertFalse(buildFilter("binary", "x:apple:1.0").apply(status));

        assertFalse(new BinarySpecPredicate("food.fruit:apple:zip:1.0").apply(status));
        assertFalse(buildFilter("binary", "food.fruit:apple:zip:1.0").apply(status));
    }

    @Test
    public void testFullBinarySpecPredicate()
    {
        SlotStatus status = new SlotStatus(UUID.randomUUID(),
                "slotName",
                URI.create("fake://localhost"),
                URI.create("fake://localhost"),
                "location",
                UNKNOWN,
                new Assignment("com.proofpoint.platform:sample-server:tar.gz:distribution:0.35-SNAPSHOT", APPLE_ASSIGNMENT.getConfig()),
                "/slotName",
                ImmutableMap.<String, Integer>of());

        assertTrue(new BinarySpecPredicate("*:*:*:*:*").apply(status));
        assertTrue(buildFilter("binary", "*:*:*:*:*").apply(status));

        assertTrue(new BinarySpecPredicate("com.proofpoint.platform:sample-server:tar.gz:distribution:0.35-SNAPSHOT").apply(status));
        assertTrue(buildFilter("binary", "com.proofpoint.platform:sample-server:tar.gz:distribution:0.35-SNAPSHOT").apply(status));

        assertTrue(new BinarySpecPredicate("*:sample-server:tar.gz:distribution:0.35-SNAPSHOT").apply(status));
        assertTrue(buildFilter("binary", "*:sample-server:tar.gz:distribution:0.35-SNAPSHOT").apply(status));

        assertTrue(new BinarySpecPredicate("com.proofpoint.platform:*:tar.gz:distribution:0.35-SNAPSHOT").apply(status));
        assertTrue(buildFilter("binary", "com.proofpoint.platform:*:tar.gz:distribution:0.35-SNAPSHOT").apply(status));

        assertTrue(new BinarySpecPredicate("com.proofpoint.platform:sample-server:*:distribution:0.35-SNAPSHOT").apply(status));
        assertTrue(buildFilter("binary", "com.proofpoint.platform:sample-server:*:distribution:0.35-SNAPSHOT").apply(status));
        assertTrue(new BinarySpecPredicate("com.proofpoint.platform:sample-server:tar.gz:*:0.35-SNAPSHOT").apply(status));
        assertTrue(buildFilter("binary", "com.proofpoint.platform:sample-server:tar.gz:*:0.35-SNAPSHOT").apply(status));
        assertTrue(new BinarySpecPredicate("com.proofpoint.platform:sample-server:tar.gz:distribution:*").apply(status));
        assertTrue(buildFilter("binary", "com.proofpoint.platform:sample-server:tar.gz:distribution:*").apply(status));


        assertTrue(new BinarySpecPredicate("c*:s*:t*:d*:0*").apply(status));
        assertTrue(buildFilter("binary", "c*:s*:t*:d*:0*").apply(status));

        assertFalse(new BinarySpecPredicate("com.proofpoint.platform:sample-server:distribution:0.35-SNAPSHOT").apply(status));
        assertFalse(buildFilter("binary", "com.proofpoint.platform:sample-server:distribution:0.35-SNAPSHOT").apply(status));
    }

    @Test
    public void testConfigSpecPredicate()
    {
        assertTrue(new ConfigSpecPredicate("@*:*").apply(status));
        assertTrue(buildFilter("config", "@*:*").apply(status));
        assertTrue(new ConfigSpecPredicate("@prod:apple:1.0").apply(status));
        assertTrue(buildFilter("config", "@prod:apple:1.0").apply(status));
        assertTrue(new ConfigSpecPredicate("@prod:apple:1.0").apply(status));
        assertTrue(buildFilter("config", "@prod:apple:1.0").apply(status));
        assertTrue(new ConfigSpecPredicate("@*:1.0").apply(status));
        assertTrue(buildFilter("config", "@*:1.0").apply(status));
        assertTrue(new ConfigSpecPredicate("@prod:apple:*").apply(status));
        assertTrue(buildFilter("config", "@prod:apple:*").apply(status));
        assertTrue(new ConfigSpecPredicate("@prod:a*:1.*").apply(status));
        assertTrue(buildFilter("config", "@prod:a*:1.*").apply(status));
        assertFalse(new ConfigSpecPredicate("@prod:apple:x:1.0").apply(status));
        assertFalse(buildFilter("config", "@prod:apple:x:1.0").apply(status));
    }
}
