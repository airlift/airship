package com.proofpoint.galaxy.coordinator;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.proofpoint.galaxy.shared.AgentStatus;
import com.proofpoint.galaxy.shared.Assignment;
import com.proofpoint.galaxy.shared.AssignmentRepresentation;
import com.proofpoint.galaxy.shared.MockUriInfo;
import com.proofpoint.galaxy.shared.SlotStatus;
import com.proofpoint.galaxy.shared.SlotStatusRepresentation;
import com.proofpoint.node.NodeInfo;
import com.proofpoint.units.Duration;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;
import java.net.URI;
import java.util.Collection;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static com.proofpoint.galaxy.coordinator.CoordinatorSlotResource.MIN_PREFIX_SIZE;
import static com.proofpoint.galaxy.coordinator.Strings.shortestUniquePrefix;
import static com.proofpoint.galaxy.shared.AgentLifecycleState.ONLINE;
import static com.proofpoint.galaxy.shared.AssignmentHelper.APPLE_ASSIGNMENT;
import static com.proofpoint.galaxy.shared.AssignmentHelper.BANANA_ASSIGNMENT;
import static com.proofpoint.galaxy.shared.ExtraAssertions.assertEqualsNoOrder;
import static com.proofpoint.galaxy.shared.SlotLifecycleState.STOPPED;
import static com.proofpoint.galaxy.shared.SlotStatus.createSlotStatus;
import static java.lang.Math.min;
import static java.util.Arrays.asList;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;

public class TestCoordinatorSlotResource
{
    private CoordinatorSlotResource resource;
    private Coordinator coordinator;
    private TestingMavenRepository repository;

    @BeforeMethod
    public void setUp()
            throws Exception
    {
        NodeInfo nodeInfo = new NodeInfo("testing");

        repository = new TestingMavenRepository();

        coordinator = new Coordinator(nodeInfo,
                new CoordinatorConfig().setStatusExpiration(new Duration(1, TimeUnit.DAYS)),
                new MockRemoteAgentFactory(),
                repository,
                new LocalProvisioner(),
                new InMemoryStateManager(),
                new MockServiceInventory());
        resource = new CoordinatorSlotResource(coordinator, repository);
    }

    @AfterMethod
    public void tearDown()
            throws Exception
    {
        repository.destroy();
    }

    @Test
    public void testGetAllSlots()
    {
        SlotStatus slot1 = createSlotStatus(UUID.randomUUID(),
                "slot1",
                URI.create("fake://localhost/v1/agent/slot/slot1"),
                URI.create("fake://localhost/v1/agent/slot/slot1"),
                "location",
                STOPPED,
                APPLE_ASSIGNMENT,
                "/slot1",
                ImmutableMap.<String, Integer>of());
        SlotStatus slot2 = createSlotStatus(UUID.randomUUID(),
                "slot2",
                URI.create("fake://localhost/v1/agent/slot/slot2"),
                URI.create("fake://localhost/v1/agent/slot/slot2"),
                "location",
                STOPPED,
                APPLE_ASSIGNMENT,
                "/slot2",
                ImmutableMap.<String, Integer>of());
        AgentStatus agentStatus = new AgentStatus(UUID.randomUUID().toString(),
                ONLINE,
                URI.create("fake://foo/"),
                URI.create("fake://foo/"),
                "unknown/location",
                "instance.type",
                ImmutableList.of(slot1, slot2),
                ImmutableMap.<String, Integer>of());
        coordinator.setAgentStatus(agentStatus);

        int prefixSize = shortestUniquePrefix(asList(slot1.getId().toString(), slot2.getId().toString()), MIN_PREFIX_SIZE);

        URI requestUri = URI.create("http://localhost/v1/slot");
        Response response = resource.getAllSlots(MockUriInfo.from(requestUri));
        assertEquals(response.getStatus(), Response.Status.OK.getStatusCode());
        assertEqualsNoOrder((Iterable<?>) response.getEntity(),
                ImmutableList.of(SlotStatusRepresentation.from(slot1, prefixSize), SlotStatusRepresentation.from(slot2, prefixSize)));
        assertNull(response.getMetadata().get("Content-Type")); // content type is set by jersey based on @Produces
    }

    @Test
    public void testGetAllSlotsWithFilter()
    {
        SlotStatus slot1 = createSlotStatus(UUID.randomUUID(),
                "slot1",
                URI.create("fake://foo/v1/agent/slot/slot1"),
                URI.create("fake://foo/v1/agent/slot/slot1"),
                "location",
                STOPPED,
                APPLE_ASSIGNMENT,
                "/slot1",
                ImmutableMap.<String, Integer>of());
        SlotStatus slot2 = createSlotStatus(UUID.randomUUID(),
                "slot2",
                URI.create("fake://bar/v1/agent/slot/slot2"),
                URI.create("fake://bar/v1/agent/slot/slot2"),
                "location",
                STOPPED,
                APPLE_ASSIGNMENT,
                "/slot2",
                ImmutableMap.<String, Integer>of());
        AgentStatus agentStatus = new AgentStatus(UUID.randomUUID().toString(),
                ONLINE,
                URI.create("fake://foo/"),
                URI.create("fake://foo/"),
                "unknown/location",
                "instance.type",
                ImmutableList.of(slot1, slot2),
                ImmutableMap.<String, Integer>of());
        coordinator.setAgentStatus(agentStatus);

        int prefixSize = shortestUniquePrefix(asList(slot1.getId().toString(), slot2.getId().toString()), MIN_PREFIX_SIZE);

        URI requestUri = URI.create("http://localhost/v1/slot?host=foo");
        Response response = resource.getAllSlots(MockUriInfo.from(requestUri));
        assertEquals(response.getStatus(), Response.Status.OK.getStatusCode());
        assertEqualsNoOrder((Iterable<?>) response.getEntity(), ImmutableList.of(SlotStatusRepresentation.from(slot1, prefixSize)));
        assertNull(response.getMetadata().get("Content-Type")); // content type is set by jersey based on @Produces
    }

    @Test
    public void testGetAllSlotEmpty()
    {
        URI requestUri = URI.create("http://localhost/v1/slot?state=unknown");
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
        for (int i = 0; i < numberOfAgents; i++) {
            final AgentStatus status = new AgentStatus(UUID.randomUUID().toString(),
                    ONLINE,
                    URI.create("fake://appleServer1/"),
                    URI.create("fake://appleServer1/"),
                    "unknown/location",
                    "instance.type",
                    ImmutableList.<SlotStatus>of(),
                    ImmutableMap.of("cpu", 8, "memory", 1024));
            coordinator.setAgentStatus(status);
        }

        UriInfo uriInfo = MockUriInfo.from("http://localhost/v1/slot/assignment?host=apple*");
        Response response = resource.install(AssignmentRepresentation.from(assignment), limit, uriInfo, null);

        assertEquals(response.getStatus(), Response.Status.OK.getStatusCode());

        Collection<SlotStatusRepresentation> slots = (Collection<SlotStatusRepresentation>) response.getEntity();
        assertEquals(slots.size(), min(numberOfAgents, limit));
        for (SlotStatusRepresentation slotRepresentation : slots) {
            SlotStatus slot = slotRepresentation.toSlotStatus();
            assertEquals(slot.getAssignment(), assignment);
            assertEquals(slot.getState(), STOPPED);
        }

        assertNull(response.getMetadata().get("Content-Type")); // content type is set by jersey based on @Produces
    }

    @Test
    public void testInstallWithinResourceLimit()
    {
        final AgentStatus status = new AgentStatus(UUID.randomUUID().toString(),
                ONLINE,
                URI.create("fake://appleServer1/"),
                URI.create("fake://appleServer1/"),
                "unknown/location",
                "instance.type",
                ImmutableList.<SlotStatus>of(),
                ImmutableMap.of("cpu", 1, "memory", 512));
        coordinator.setAgentStatus(status);

        UriInfo uriInfo = MockUriInfo.from("http://localhost/v1/slot/assignment");
        Response response = resource.install(AssignmentRepresentation.from(APPLE_ASSIGNMENT), 1, uriInfo, null);

        assertEquals(response.getStatus(), Response.Status.OK.getStatusCode());

        Collection<SlotStatusRepresentation> slots = (Collection<SlotStatusRepresentation>) response.getEntity();
        assertEquals(slots.size(), 1);
        for (SlotStatusRepresentation slotRepresentation : slots) {
            assertAppleSlot(slotRepresentation);
        }

        assertNull(response.getMetadata().get("Content-Type")); // content type is set by jersey based on @Produces
    }

    @Test
    public void testInstallNotEnoughResources()
    {
        final AgentStatus status = new AgentStatus(UUID.randomUUID().toString(),
                ONLINE,
                URI.create("fake://appleServer1/"),
                URI.create("fake://appleServer1/"),
                "unknown/location",
                "instance.type",
                ImmutableList.<SlotStatus>of(),
                ImmutableMap.<String, Integer>of());
        coordinator.setAgentStatus(status);

        UriInfo uriInfo = MockUriInfo.from("http://localhost/v1/slot/assignment");
        Response response = resource.install(AssignmentRepresentation.from(APPLE_ASSIGNMENT), 1, uriInfo, null);

        assertEquals(response.getStatus(), Response.Status.OK.getStatusCode());

        Collection<SlotStatusRepresentation> slots = (Collection<SlotStatusRepresentation>) response.getEntity();
        assertEquals(slots.size(), 0);
    }

    @Test
    public void testInstallResourcesConsumed()
    {
        final AgentStatus status = new AgentStatus(UUID.randomUUID().toString(),
                ONLINE,
                URI.create("fake://appleServer1/"),
                URI.create("fake://appleServer1/"),
                "unknown/location",
                "instance.type",
                ImmutableList.<SlotStatus>of(),
                ImmutableMap.of("cpu", 1, "memory", 512));
        coordinator.setAgentStatus(status);

        UriInfo uriInfo = MockUriInfo.from("http://localhost/v1/slot/assignment");

        // install an apple server
        Response response = resource.install(AssignmentRepresentation.from(APPLE_ASSIGNMENT), 1, uriInfo, null);
        assertEquals(response.getStatus(), Response.Status.OK.getStatusCode());
        Collection<SlotStatusRepresentation> slots = (Collection<SlotStatusRepresentation>) response.getEntity();
        assertEquals(slots.size(), 1);
        assertAppleSlot(Iterables.get(slots, 0));

        // try to install a banana server which will fail
        response = resource.install(AssignmentRepresentation.from(BANANA_ASSIGNMENT), 1, uriInfo, null);
        assertEquals(response.getStatus(), Response.Status.OK.getStatusCode());
        slots = (Collection<SlotStatusRepresentation>) response.getEntity();
        assertEquals(slots.size(), 0);
    }

    private void assertAppleSlot(SlotStatusRepresentation slotRepresentation)
    {
        SlotStatus slot = slotRepresentation.toSlotStatus();
        assertEquals(slot.getAssignment(), APPLE_ASSIGNMENT);
        assertEquals(slot.getState(), STOPPED);
        assertEquals(slot.getResources(), ImmutableMap.of("cpu", 1, "memory", 512));
    }
}
