package com.proofpoint.galaxy.coordinator;

import com.google.common.collect.ImmutableList;
import com.proofpoint.galaxy.shared.AgentStatus;
import com.proofpoint.galaxy.shared.Assignment;
import com.proofpoint.galaxy.shared.AssignmentRepresentation;
import com.proofpoint.galaxy.shared.MockUriInfo;
import com.proofpoint.galaxy.shared.SlotLifecycleState;
import com.proofpoint.galaxy.shared.SlotStatus;
import com.proofpoint.galaxy.shared.SlotStatusRepresentation;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;
import java.net.URI;
import java.util.Collection;
import java.util.UUID;

import static com.proofpoint.galaxy.coordinator.RepoHelper.MOCK_BINARY_REPO;
import static com.proofpoint.galaxy.coordinator.RepoHelper.MOCK_CONFIG_REPO;
import static com.proofpoint.galaxy.shared.AgentLifecycleState.*;
import static com.proofpoint.galaxy.shared.AssignmentHelper.APPLE_ASSIGNMENT;
import static com.proofpoint.galaxy.shared.ExtraAssertions.assertEqualsNoOrder;
import static java.lang.Math.min;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;

public class TestCoordinatorSlotResource
{
    private CoordinatorSlotResource resource;
    private Coordinator coordinator;

    @BeforeMethod
    public void setUp()
            throws Exception
    {
        coordinator = new Coordinator(new MockRemoteAgentFactory(),
                MOCK_BINARY_REPO,
                MOCK_CONFIG_REPO,
                new LocalConfigRepository(new CoordinatorConfig(), null),
                new GitConfigRepository(new GitConfigRepositoryConfig(), null));
        resource = new CoordinatorSlotResource(coordinator,
                MOCK_BINARY_REPO,
                MOCK_CONFIG_REPO,
                new LocalConfigRepository(new CoordinatorConfig(), null),
                new GitConfigRepository(new GitConfigRepositoryConfig(), null));
    }

    @Test
    public void testGetAllSlots()
    {
        SlotStatus slot1 = new SlotStatus(UUID.randomUUID(), "slot1", URI.create("fake://localhost/v1/agent/slot/slot1"));
        SlotStatus slot2 = new SlotStatus(UUID.randomUUID(), "slot2", URI.create("fake://localhost/v1/agent/slot/slot2"));
        AgentStatus agentStatus = new AgentStatus(UUID.randomUUID(), ONLINE, URI.create("fake://foo/"), ImmutableList.of(slot1, slot2));
        coordinator.updateAgentStatus(agentStatus);

        URI requestUri = URI.create("http://localhost/v1/slot");
        Response response = resource.getAllSlots(MockUriInfo.from(requestUri));
        assertEquals(response.getStatus(), Response.Status.OK.getStatusCode());
        assertEqualsNoOrder((Iterable<?>) response.getEntity(), ImmutableList.of(SlotStatusRepresentation.from(slot1), SlotStatusRepresentation.from(slot2)));
        assertNull(response.getMetadata().get("Content-Type")); // content type is set by jersey based on @Produces
    }

    @Test
    public void testGetAllSlotsWithFilter()
    {
        SlotStatus slot1 = new SlotStatus(UUID.randomUUID(), "slot1", URI.create("fake://foo/v1/agent/slot/slot1"));
        SlotStatus slot2 = new SlotStatus(UUID.randomUUID(), "slot2", URI.create("fake://bar/v1/agent/slot/slot2"));
        AgentStatus agentStatus = new AgentStatus(UUID.randomUUID(), ONLINE, URI.create("fake://foo/"), ImmutableList.of(slot1, slot2));
        coordinator.updateAgentStatus(agentStatus);

        URI requestUri = URI.create("http://localhost/v1/slot?host=foo");
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


    @Test
    public void testInstallOne()
    {
        testInstall(1, 1, APPLE_ASSIGNMENT);
    }

    @Test
    public void testInstallLimit()
    {
        testInstall(10, 3, APPLE_ASSIGNMENT);
    }

    @Test
    public void testInstallNotEnoughAgents()
    {
        testInstall(3, 10, APPLE_ASSIGNMENT);
    }

    public void testInstall(int numberOfAgents, int limit, Assignment assignment)
    {
        // clear the agents since install creates slots on the fly
        // todo remove then when we drop support for assign
        for (RemoteAgent agent : coordinator.getAgents()) {
            coordinator.removeAgent(agent.status().getAgentId());
        }

        for (int i = 0; i < numberOfAgents; i++) {
            coordinator.updateAgentStatus(new AgentStatus(UUID.randomUUID(),
                    ONLINE,
                    URI.create("fake://appleServer1/"),
                    ImmutableList.<SlotStatus>of()));
        }

        UriInfo uriInfo = MockUriInfo.from("http://localhost/v1/slot/assignment?host=apple*");
        Response response = resource.install(AssignmentRepresentation.from(assignment), limit, uriInfo);

        assertEquals(response.getStatus(), Response.Status.OK.getStatusCode());

        Collection<SlotStatusRepresentation> slots = (Collection<SlotStatusRepresentation>) response.getEntity();
        assertEquals(slots.size(), min(numberOfAgents, limit));
        for (SlotStatusRepresentation slotRepresentation : slots) {
            SlotStatus slot = slotRepresentation.toSlotStatus();
            assertEquals(slot.getAssignment(), assignment);
            assertEquals(slot.getState(), SlotLifecycleState.STOPPED);
        }

        assertNull(response.getMetadata().get("Content-Type")); // content type is set by jersey based on @Produces
    }
}
