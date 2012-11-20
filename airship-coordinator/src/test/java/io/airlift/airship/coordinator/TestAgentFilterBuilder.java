package io.airlift.airship.coordinator;

import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import io.airlift.airship.coordinator.AgentFilterBuilder.AssignablePredicate;
import io.airlift.airship.coordinator.AgentFilterBuilder.HostPredicate;
import io.airlift.airship.coordinator.AgentFilterBuilder.MachinePredicate;
import io.airlift.airship.coordinator.AgentFilterBuilder.SlotUuidPredicate;
import io.airlift.airship.coordinator.AgentFilterBuilder.StatePredicate;
import io.airlift.airship.coordinator.AgentFilterBuilder.UuidPredicate;
import io.airlift.airship.shared.AgentStatus;
import io.airlift.airship.shared.MockUriInfo;
import io.airlift.airship.shared.Repository;
import io.airlift.airship.shared.SlotStatus;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.net.URI;
import java.util.List;
import java.util.UUID;

import static io.airlift.airship.shared.AgentLifecycleState.OFFLINE;
import static io.airlift.airship.shared.AgentLifecycleState.ONLINE;
import static io.airlift.airship.shared.AssignmentHelper.APPLE_ASSIGNMENT;
import static io.airlift.airship.shared.AssignmentHelper.BANANA_ASSIGNMENT;
import static io.airlift.airship.shared.SlotLifecycleState.UNKNOWN;
import static io.airlift.airship.shared.SlotStatus.createSlotStatus;
import static java.util.Arrays.asList;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

public class TestAgentFilterBuilder
{
    private AgentStatus status;

    @BeforeMethod
    public void setUp()
            throws Exception
    {
        status = new AgentStatus("agent-id",
                ONLINE,
                "instance-id",
                URI.create("internal://10.0.0.1"),
                URI.create("external://localhost"),
                "/unknown/location",
                "instance.type",
                ImmutableList.<SlotStatus>of(createSlotStatus(UUID.fromString("12345678-1234-1234-1234-123456789012"),
                        URI.create("fake://localhost"),
                        URI.create("fake://localhost"),
                        "instance",
                        "/location",
                        UNKNOWN,
                        APPLE_ASSIGNMENT,
                        "/install-path",
                        ImmutableMap.<String, Integer>of())),
                ImmutableMap.<String, Integer>of(
                        "memory", 2048,
                        "cpu", 4));
    }

    private Predicate<AgentStatus> buildFilter(String key, String value, List<String> agentUuids, List<UUID> slotUuids)
    {
        return AgentFilterBuilder.build(MockUriInfo.from("fake://localhost?" + key + "=" + value), agentUuids, slotUuids);
    }

    private Predicate<AgentStatus> buildFilter(String key, String value)
    {
        return buildFilter(key, value, ImmutableList.<String>of(), ImmutableList.<UUID>of());
    }

    private Predicate<AgentStatus> buildFilter(String key,
            String value,
            boolean allowDuplicateInstallationsOnAnAgent,
            Repository repository)
    {
        return AgentFilterBuilder.build(MockUriInfo.from("fake://localhost?" + key + "=" + value), ImmutableList.<String>of(), ImmutableList.<UUID>of(), allowDuplicateInstallationsOnAnAgent, repository);
    }

    @Test
    public void testAll()
    {
        assertTrue(AgentFilterBuilder.build(MockUriInfo.from("fake://localhost?all&state=online"), ImmutableList.<String>of(), ImmutableList.<UUID>of()).apply(status));
        assertTrue(AgentFilterBuilder.build(MockUriInfo.from("fake://localhost?all&state=offline"), ImmutableList.<String>of(), ImmutableList.<UUID>of()).apply(status));
        assertTrue(AgentFilterBuilder.build(MockUriInfo.from("fake://localhost?all&host=host"), ImmutableList.<String>of(), ImmutableList.<UUID>of()).apply(status));
    }

    @Test
    public void testUuidPredicate()
    {
        assertTrue(new UuidPredicate("agent-id", ImmutableList.<String>of("agent-id", "apple")).apply(status));
        assertTrue(buildFilter("uuid", "agent-id", ImmutableList.<String>of("agent-id", "apple"), ImmutableList.<UUID>of()).apply(status));

        assertTrue(new UuidPredicate("age", ImmutableList.<String>of("agent-id", "apple")).apply(status));
        assertTrue(buildFilter("uuid", "age", ImmutableList.<String>of("agent-id", "apple"), ImmutableList.<UUID>of()).apply(status));

        assertFalse(new UuidPredicate("unknown", ImmutableList.<String>of("agent-id", "apple")).apply(status));
        assertFalse(buildFilter("uuid", "unknown", ImmutableList.<String>of("agent-id", "apple"), ImmutableList.<UUID>of()).apply(status));

        try {
            assertFalse(new UuidPredicate("a", ImmutableList.<String>of("agent-id", "apple")).apply(status));
            fail("expected IllegalArgumentException");
        }
        catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().toLowerCase().contains("ambiguous expansion"));
        }
        try {
            assertFalse(buildFilter("uuid", "a", ImmutableList.<String>of("agent-id", "apple"), ImmutableList.<UUID>of()).apply(status));
            fail("expected IllegalArgumentException");
        }
        catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().toLowerCase().contains("ambiguous expansion"));
        }
    }

    @Test
    public void testStateSpecPredicate()
    {
        assertTrue(new StatePredicate(ONLINE).apply(status));
        assertTrue(buildFilter("state", "online").apply(status));
        assertFalse(new StatePredicate(OFFLINE).apply(status));
        assertFalse(buildFilter("state", "offline").apply(status));
    }

    @Test
    public void testSlotUuidPredicate()
    {
        assertTrue(new SlotUuidPredicate(UUID.fromString("12345678-1234-1234-1234-123456789012")).apply(status));
        assertTrue(buildFilter("slotUuid", "12345678-1234-1234-1234-123456789012", ImmutableList.<String>of(), asList(UUID.fromString("12345678-1234-1234-1234-123456789012"))).apply(status));
        assertFalse(new SlotUuidPredicate(UUID.fromString("00000000-0000-0000-0000-000000000000")).apply(status));
        assertFalse(buildFilter("slotUuid", "00000000-0000-0000-0000-000000000000").apply(status));
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
        assertTrue(new HostPredicate("10.0.0.1").apply(status));
        assertTrue(buildFilter("host", "10.0.0.1").apply(status));
        assertFalse(new HostPredicate("10.1.2.3").apply(status));
        assertFalse(buildFilter("host", "10.1.2.3").apply(status));
    }

    @Test
    public void testMachineSpecPredicate()
    {
        assertTrue(new MachinePredicate("instance-id").apply(status));
        assertTrue(buildFilter("machine", "instance-id").apply(status));
        assertTrue(new MachinePredicate("inst*").apply(status));
        assertTrue(buildFilter("machine", "inst*").apply(status));

        assertFalse(new MachinePredicate("INSTANCE-ID").apply(status));
        assertFalse(buildFilter("machine", "INSTANCE-ID").apply(status));
        assertFalse(new MachinePredicate("INST*").apply(status));
        assertFalse(buildFilter("machine", "INST*").apply(status));
    }

    @Test
    public void testAssignablePredicate()
            throws Exception
    {
        TestingMavenRepository repository = new TestingMavenRepository();
        try {
            assertTrue(new AssignablePredicate(BANANA_ASSIGNMENT, true, repository).apply(status));
            assertTrue(buildFilter("assignable", BANANA_ASSIGNMENT.getBinary() + BANANA_ASSIGNMENT.getConfig(), true, repository).apply(status));
            assertTrue(new AssignablePredicate(BANANA_ASSIGNMENT, false, repository).apply(status));
            assertTrue(buildFilter("assignable", BANANA_ASSIGNMENT.getBinary() + BANANA_ASSIGNMENT.getConfig(), false, repository).apply(status));
            assertTrue(new AssignablePredicate(APPLE_ASSIGNMENT, true, repository).apply(status));
            assertTrue(buildFilter("assignable", APPLE_ASSIGNMENT.getBinary() + APPLE_ASSIGNMENT.getConfig(), true, repository).apply(status));
            assertFalse(new AssignablePredicate(APPLE_ASSIGNMENT, false, repository).apply(status));
            assertFalse(buildFilter("assignable", APPLE_ASSIGNMENT.getBinary() + APPLE_ASSIGNMENT.getConfig(), false, repository).apply(status));

            status = status.changeSlotStatus(createSlotStatus(UUID.fromString("99999999-1234-1234-1234-123456789012"),
                    URI.create("fake://localhost"),
                    URI.create("fake://localhost"),
                    "instance",
                    "/location",
                    UNKNOWN,
                    APPLE_ASSIGNMENT,
                    "/install-path",
                    ImmutableMap.<String, Integer>of(
                            "memory", 2048,
                            "cpu", 4
                    ))
            );

            assertFalse(new AssignablePredicate(BANANA_ASSIGNMENT, true, repository).apply(status));
            assertFalse(buildFilter("assignable", BANANA_ASSIGNMENT.getBinary() + BANANA_ASSIGNMENT.getConfig(), true, repository).apply(status));
        }
        finally {
            repository.destroy();
        }
    }

    @Test
    public void testNullFields()
    {
        status = new AgentStatus("agent-id", ONLINE, null, null, null, null, null,
                ImmutableList.<SlotStatus>of(createSlotStatus(UUID.randomUUID(),
                        null, null, null, "/location", UNKNOWN, null, null,
                        ImmutableMap.<String, Integer>of())),
                ImmutableMap.<String, Integer>of());

        assertTrue(new UuidPredicate("agent-id", asList("agent-id", null)).apply(status));
        assertTrue(buildFilter("uuid", "agent-id", asList("agent-id", null), ImmutableList.<UUID>of()).apply(status));

        assertFalse(new HostPredicate("localhost").apply(status));
        assertFalse(buildFilter("host", "localhost").apply(status));

        assertFalse(new MachinePredicate("instance-id").apply(status));
        assertFalse(buildFilter("machine", "instance-id").apply(status));
    }
}
