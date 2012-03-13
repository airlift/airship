package com.proofpoint.galaxy.coordinator;

import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.proofpoint.galaxy.coordinator.AgentFilterBuilder.HostPredicate;
import com.proofpoint.galaxy.coordinator.AgentFilterBuilder.SlotUuidPredicate;
import com.proofpoint.galaxy.coordinator.AgentFilterBuilder.StatePredicate;
import com.proofpoint.galaxy.coordinator.AgentFilterBuilder.UuidPredicate;
import com.proofpoint.galaxy.shared.AgentStatus;
import com.proofpoint.galaxy.shared.MockUriInfo;
import com.proofpoint.galaxy.shared.SlotStatus;
import org.testng.annotations.Test;

import java.net.URI;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static com.proofpoint.galaxy.shared.AgentLifecycleState.OFFLINE;
import static com.proofpoint.galaxy.shared.AgentLifecycleState.ONLINE;
import static com.proofpoint.galaxy.shared.AssignmentHelper.APPLE_ASSIGNMENT;
import static com.proofpoint.galaxy.shared.SlotLifecycleState.UNKNOWN;
import static com.proofpoint.galaxy.shared.SlotStatus.createSlotStatus;
import static java.util.Arrays.asList;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

public class TestAgentFilterBuilder
{
    AgentStatus status = new AgentStatus("agent-id",
            ONLINE,
            "instance-id",
            URI.create("internal://10.0.0.1"),
            URI.create("external://localhost"),
            "unknown/location",
            "instance.type",
            ImmutableList.<SlotStatus>of(createSlotStatus(UUID.fromString("12345678-1234-1234-1234-123456789012"),
                    "slotName",
                    URI.create("fake://localhost"),
                    URI.create("fake://localhost"),
                    "instance",
                    "/location",
                    UNKNOWN,
                    APPLE_ASSIGNMENT,
                    "/slotName",
                    ImmutableMap.<String, Integer>of())),
            ImmutableMap.<String, Integer>of());


    private Predicate<AgentStatus> buildFilter(String key, String value, List<UUID> uuids)
    {
        return AgentFilterBuilder.build(MockUriInfo.from("fake://localhost?" + key + "=" + value), uuids);
    }

    private Predicate<AgentStatus> buildFilter(String key, String value)
    {
        return buildFilter(key, value, Collections.<UUID>emptyList());
    }

    @Test
    public void testAll()
    {
        assertTrue(AgentFilterBuilder.build(MockUriInfo.from("fake://localhost?all&state=online"), ImmutableList.<UUID>of()).apply(status));
        assertTrue(AgentFilterBuilder.build(MockUriInfo.from("fake://localhost?all&state=offline"), ImmutableList.<UUID>of()).apply(status));
        assertTrue(AgentFilterBuilder.build(MockUriInfo.from("fake://localhost?all&host=host"), ImmutableList.<UUID>of()).apply(status));
    }

    @Test
    public void testUuidPredicate()
    {
        assertTrue(new UuidPredicate("agent-id").apply(status));
        assertTrue(buildFilter("uuid", "agent-id").apply(status));
        assertFalse(new UuidPredicate("unknown").apply(status));
        assertFalse(buildFilter("uuid", "unknown").apply(status));
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
        assertTrue(buildFilter("slotUuid", "12345678-1234-1234-1234-123456789012", asList(UUID.fromString("12345678-1234-1234-1234-123456789012"))).apply(status));
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
}
