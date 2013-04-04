package io.airlift.airship.coordinator;

import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import io.airlift.airship.coordinator.SlotFilterBuilder.SlotUuidPredicate;
import io.airlift.airship.shared.Assignment;
import io.airlift.airship.shared.MockUriInfo;
import io.airlift.airship.shared.SlotStatus;
import io.airlift.airship.coordinator.SlotFilterBuilder.BinarySpecPredicate;
import io.airlift.airship.coordinator.SlotFilterBuilder.ConfigSpecPredicate;
import io.airlift.airship.coordinator.SlotFilterBuilder.HostPredicate;
import io.airlift.airship.coordinator.SlotFilterBuilder.StatePredicate;
import org.testng.annotations.Test;

import java.net.URI;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static io.airlift.airship.shared.AssignmentHelper.APPLE_ASSIGNMENT;
import static io.airlift.airship.shared.SlotLifecycleState.RUNNING;
import static io.airlift.airship.shared.SlotLifecycleState.UNKNOWN;
import static io.airlift.airship.shared.SlotStatus.createSlotStatus;
import static java.util.Arrays.asList;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

public class TestSlotFilterBuilder
{
    private final SlotStatus status = SlotStatus.createSlotStatusWithExpectedState(UUID.fromString("12345678-1234-1234-1234-123456789012"),
            URI.create("fake://localhost"),
            URI.create("fake://localhost"),
            "instance",
            "/location",
            UNKNOWN,
            APPLE_ASSIGNMENT,
            "/install-path",
            ImmutableMap.<String, Integer>of(),
            null,
            null,
            null);


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

        assertFalse(buildFilter("!state", "unknown").apply(status));
        assertFalse(buildFilter("!state", "u").apply(status));
        assertFalse(buildFilter("!state", "UnKnown").apply(status));
        assertFalse(buildFilter("!state", "U").apply(status));
        assertTrue(buildFilter("!state", "running").apply(status));
        assertTrue(buildFilter("!state", "r").apply(status));
    }

    @Test
    public void testSlotUuidPredicate()
    {
        assertTrue(new SlotUuidPredicate(UUID.fromString("12345678-1234-1234-1234-123456789012")).apply(status));
        assertTrue(buildFilter("uuid", "12345678-1234-1234-1234-123456789012", asList(UUID.fromString("12345678-1234-1234-1234-123456789012"))).apply(status));
        assertFalse(new SlotUuidPredicate(UUID.fromString("00000000-0000-0000-0000-000000000000")).apply(status));
        assertFalse(buildFilter("uuid", "00000000-0000-0000-0000-000000000000").apply(status));

        assertFalse(buildFilter("!uuid", "12345678-1234-1234-1234-123456789012", asList(UUID.fromString("12345678-1234-1234-1234-123456789012"))).apply(status));
        assertTrue(buildFilter("!uuid", "00000000-0000-0000-0000-000000000000").apply(status));
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

        assertFalse(buildFilter("!host", "localhost").apply(status));
        assertFalse(buildFilter("!host", "LOCALHOST").apply(status));
        assertFalse(buildFilter("!host", "LocalHost").apply(status));
        assertFalse(buildFilter("!host", "local*").apply(status));
        assertFalse(buildFilter("!host", "LocAL*").apply(status));
        assertTrue(buildFilter("!host", "foo").apply(status));

        assertFalse(buildFilter("!host", "127.0.0.1").apply(status));
        assertTrue(buildFilter("!host", "10.1.2.3").apply(status));
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

        assertFalse(buildFilter("!binary", "*").apply(status));
        assertFalse(buildFilter("!binary", "food.fruit:apple:1.0").apply(status));
        assertFalse(buildFilter("!binary", "*apple*").apply(status));
        assertFalse(buildFilter("!binary", "*:apple:1.0").apply(status));
        assertFalse(buildFilter("!binary", "food.fruit:*:1.0").apply(status));
        assertFalse(buildFilter("!binary", "food.fruit:apple:*").apply(status));
        assertFalse(buildFilter("!binary", "f*:a*:1.*").apply(status));
        assertTrue(buildFilter("!binary", "*banana*").apply(status));
        assertTrue(buildFilter("!binary", "x:apple:1.0").apply(status));
        assertTrue(buildFilter("!binary", "food.fruit:apple:zip:1.0").apply(status));
    }

    @Test
    public void testFullBinarySpecPredicate()
    {
        SlotStatus status = createSlotStatus(UUID.randomUUID(),
                URI.create("fake://localhost"),
                URI.create("fake://localhost"),
                "instance",
                "/location",
                UNKNOWN,
                new Assignment("io.airlift:sample-server:tar.gz:distribution:0.35-SNAPSHOT", APPLE_ASSIGNMENT.getConfig()),
                "/install-path",
                ImmutableMap.<String, Integer>of());

        assertTrue(new BinarySpecPredicate("*:*:*:*:*").apply(status));
        assertTrue(buildFilter("binary", "*:*:*:*:*").apply(status));

        assertTrue(new BinarySpecPredicate("io.airlift:sample-server:tar.gz:distribution:0.35-SNAPSHOT").apply(status));
        assertTrue(buildFilter("binary", "io.airlift:sample-server:tar.gz:distribution:0.35-SNAPSHOT").apply(status));

        assertTrue(new BinarySpecPredicate("*:sample-server:tar.gz:distribution:0.35-SNAPSHOT").apply(status));
        assertTrue(buildFilter("binary", "*:sample-server:tar.gz:distribution:0.35-SNAPSHOT").apply(status));

        assertTrue(new BinarySpecPredicate("io.airlift:*:tar.gz:distribution:0.35-SNAPSHOT").apply(status));
        assertTrue(buildFilter("binary", "io.airlift:*:tar.gz:distribution:0.35-SNAPSHOT").apply(status));

        assertTrue(new BinarySpecPredicate("io.airlift:sample-server:*:distribution:0.35-SNAPSHOT").apply(status));
        assertTrue(buildFilter("binary", "io.airlift:sample-server:*:distribution:0.35-SNAPSHOT").apply(status));
        assertTrue(new BinarySpecPredicate("io.airlift:sample-server:tar.gz:*:0.35-SNAPSHOT").apply(status));
        assertTrue(buildFilter("binary", "io.airlift:sample-server:tar.gz:*:0.35-SNAPSHOT").apply(status));
        assertTrue(new BinarySpecPredicate("io.airlift:sample-server:tar.gz:distribution:*").apply(status));
        assertTrue(buildFilter("binary", "io.airlift:sample-server:tar.gz:distribution:*").apply(status));


        assertTrue(new BinarySpecPredicate("i*:s*:t*:d*:0*").apply(status));
        assertTrue(buildFilter("binary", "i*:s*:t*:d*:0*").apply(status));

        assertFalse(new BinarySpecPredicate("io.airlift:sample-server:distribution:0.35-SNAPSHOT").apply(status));
        assertFalse(buildFilter("binary", "io.airlift:sample-server:distribution:0.35-SNAPSHOT").apply(status));

        assertFalse(buildFilter("!binary", "*:*:*:*:*").apply(status));
        assertFalse(buildFilter("!binary", "io.airlift:sample-server:tar.gz:distribution:0.35-SNAPSHOT").apply(status));
        assertFalse(buildFilter("!binary", "*:sample-server:tar.gz:distribution:0.35-SNAPSHOT").apply(status));
        assertFalse(buildFilter("!binary", "io.airlift:*:tar.gz:distribution:0.35-SNAPSHOT").apply(status));
        assertFalse(buildFilter("!binary", "io.airlift:sample-server:*:distribution:0.35-SNAPSHOT").apply(status));
        assertFalse(buildFilter("!binary", "io.airlift:sample-server:tar.gz:*:0.35-SNAPSHOT").apply(status));
        assertFalse(buildFilter("!binary", "io.airlift:sample-server:tar.gz:distribution:*").apply(status));
        assertFalse(buildFilter("!binary", "i*:s*:t*:d*:0*").apply(status));
        assertTrue(buildFilter("!binary", "io.airlift:sample-server:distribution:0.35-SNAPSHOT").apply(status));
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

        assertFalse(buildFilter("!config", "@*:*").apply(status));
        assertFalse(buildFilter("!config", "@prod:apple:1.0").apply(status));
        assertFalse(buildFilter("!config", "@prod:apple:1.0").apply(status));
        assertFalse(buildFilter("!config", "@*:1.0").apply(status));
        assertFalse(buildFilter("!config", "@prod:apple:*").apply(status));
        assertFalse(buildFilter("!config", "@prod:a*:1.*").apply(status));
        assertTrue(buildFilter("!config", "@prod:apple:x:1.0").apply(status));
    }
}
