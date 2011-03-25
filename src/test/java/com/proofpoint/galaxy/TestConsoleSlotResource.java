package com.proofpoint.galaxy;

import com.google.common.collect.ImmutableList;
import com.proofpoint.units.Duration;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import javax.ws.rs.core.Response;
import java.net.URI;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static com.proofpoint.galaxy.ExtraAssertions.assertEqualsNoOrder;
import static com.proofpoint.galaxy.LifecycleState.UNASSIGNED;
import static com.proofpoint.galaxy.LifecycleState.UNKNOWN;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;

public class TestConsoleSlotResource
{
    private ConsoleSlotResource resource;
    private Console console;

    @BeforeMethod
    public void setUp()
            throws Exception
    {
        console = new Console(new MockRemoteSlotFactory(), new ConsoleConfig().setStatusExpiration(new Duration(100, TimeUnit.DAYS)));
        resource = new ConsoleSlotResource(console);
    }

    private SlotStatus createSlotStatus(String slotName, LifecycleState state)
    {
        return new SlotStatus(UUID.randomUUID(), slotName, URI.create("fake://localhost/v1/slot/" + slotName), state);
    }

    @Test
    public void testGetAllSlots()
    {
        SlotStatus slot1 = createSlotStatus("slot1", UNASSIGNED);
        SlotStatus slot2 = createSlotStatus("slot2", UNASSIGNED);
        AgentStatus agentStatus = new AgentStatus(UUID.randomUUID(), ImmutableList.of(slot1, slot2));
        console.updateAgentStatus(agentStatus);

        URI requestUri = URI.create("http://localhost/v1/slot");
        Response response = resource.getAllSlots(MockUriInfo.from(requestUri));
        assertEquals(response.getStatus(), Response.Status.OK.getStatusCode());
        assertEqualsNoOrder((Iterable<?>) response.getEntity(), ImmutableList.of(SlotStatusRepresentation.from(slot1), SlotStatusRepresentation.from(slot2)));
        assertNull(response.getMetadata().get("Content-Type")); // content type is set by jersey based on @Produces
    }

    @Test
    public void testGetAllSlotsWithFilter()
    {
        SlotStatus slot1 = createSlotStatus("slot1", UNASSIGNED);
        SlotStatus slot2 = createSlotStatus("slot2", UNKNOWN);
        AgentStatus agentStatus = new AgentStatus(UUID.randomUUID(), ImmutableList.of(slot1, slot2));
        console.updateAgentStatus(agentStatus);

        URI requestUri = URI.create("http://localhost/v1/slot?state=unassigned");
        Response response = resource.getAllSlots(MockUriInfo.from(requestUri));
        assertEquals(response.getStatus(), Response.Status.OK.getStatusCode());
        assertEqualsNoOrder((Iterable<?>) response.getEntity(), ImmutableList.of(SlotStatusRepresentation.from(slot1)));
        assertNull(response.getMetadata().get("Content-Type")); // content type is set by jersey based on @Produces
    }

    @Test
    public void testGetAllSlotEmpty()
    {
        URI requestUri = URI.create("http://localhost/v1/slot?state=unassigned");
        Response response = resource.getAllSlots(MockUriInfo.from(requestUri));
        assertEquals(response.getStatus(), Response.Status.OK.getStatusCode());
        assertEqualsNoOrder((Iterable<?>) response.getEntity(), ImmutableList.of());
        assertNull(response.getMetadata().get("Content-Type")); // content type is set by jersey based on @Produces
    }
}
