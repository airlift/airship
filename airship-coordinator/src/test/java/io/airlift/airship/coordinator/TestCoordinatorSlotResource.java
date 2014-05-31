package io.airlift.airship.coordinator;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import io.airlift.airship.shared.AgentStatus;
import io.airlift.airship.shared.Assignment;
import io.airlift.airship.shared.AssignmentRepresentation;
import io.airlift.airship.shared.MockUriInfo;
import io.airlift.airship.shared.SlotStatus;
import io.airlift.airship.shared.SlotStatusRepresentation;
import io.airlift.http.server.HttpServerConfig;
import io.airlift.http.server.HttpServerInfo;
import io.airlift.node.NodeInfo;
import io.airlift.units.Duration;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;

import java.net.URI;
import java.util.Collection;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static io.airlift.airship.coordinator.CoordinatorSlotResource.MIN_PREFIX_SIZE;
import static io.airlift.airship.shared.AgentLifecycleState.ONLINE;
import static io.airlift.airship.shared.AssignmentHelper.APPLE_ASSIGNMENT;
import static io.airlift.airship.shared.AssignmentHelper.BANANA_ASSIGNMENT;
import static io.airlift.airship.shared.ExtraAssertions.assertEqualsNoOrder;
import static io.airlift.airship.shared.SlotLifecycleState.STOPPED;
import static io.airlift.airship.shared.SlotStatus.createSlotStatus;
import static io.airlift.airship.shared.Strings.shortestUniquePrefix;
import static java.lang.Math.min;
import static java.util.Arrays.asList;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;

public class TestCoordinatorSlotResource
{
    private CoordinatorSlotResource resource;
    private Coordinator coordinator;
    private TestingMavenRepository repository;
    private MockProvisioner provisioner;

    @BeforeMethod
    public void setUp()
            throws Exception
    {
        NodeInfo nodeInfo = new NodeInfo("testing");

        repository = new TestingMavenRepository();

        provisioner = new MockProvisioner();
        coordinator = new Coordinator(nodeInfo,
                new HttpServerInfo(new HttpServerConfig(), nodeInfo),
                new CoordinatorConfig().setStatusExpiration(new Duration(1, TimeUnit.DAYS)),
                provisioner.getCoordinatorFactory(),
                provisioner.getAgentFactory(),
                repository,
                provisioner,
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
                URI.create("fake://localhost/v1/agent/slot/slot1"),
                URI.create("fake://localhost/v1/agent/slot/slot1"),
                "instance-id",
                "/location",
                STOPPED,
                APPLE_ASSIGNMENT,
                "/slot1",
                ImmutableMap.<String, Integer>of());
        SlotStatus slot2 = createSlotStatus(UUID.randomUUID(),
                URI.create("fake://localhost/v1/agent/slot/slot2"),
                URI.create("fake://localhost/v1/agent/slot/slot2"),
                "instance-id",
                "/location",
                STOPPED,
                APPLE_ASSIGNMENT,
                "/slot2",
                ImmutableMap.<String, Integer>of());
        AgentStatus agentStatus = new AgentStatus(UUID.randomUUID().toString(),
                ONLINE,
                "instance-id",
                URI.create("fake://foo/"),
                URI.create("fake://foo/"),
                "/unknown/location",
                "instance.type",
                ImmutableList.of(slot1, slot2),
                ImmutableMap.<String, Integer>of());
        provisioner.addAgents(agentStatus);
        coordinator.updateAllAgentsAndWait();

        int prefixSize = shortestUniquePrefix(asList(slot1.getId().toString(), slot2.getId().toString()), MIN_PREFIX_SIZE);

        URI requestUri = URI.create("http://localhost/v1/slot");
        Response response = resource.getAllSlots(MockUriInfo.from(requestUri));
        assertEquals(response.getStatus(), Response.Status.OK.getStatusCode());
        assertEqualsNoOrder((Iterable<?>) response.getEntity(),
                ImmutableList.of(SlotStatusRepresentation.from(slot1, prefixSize, repository), SlotStatusRepresentation.from(slot2, prefixSize, repository)));
        assertNull(response.getMetadata().get("Content-Type")); // content type is set by jersey based on @Produces
    }

    @Test
    public void testGetAllSlotsWithFilter()
    {
        SlotStatus slot1 = createSlotStatus(UUID.randomUUID(),
                URI.create("fake://foo/v1/agent/slot/slot1"),
                URI.create("fake://foo/v1/agent/slot/slot1"),
                "instance-id",
                "/location",
                STOPPED,
                APPLE_ASSIGNMENT,
                "/slot1",
                ImmutableMap.<String, Integer>of());
        SlotStatus slot2 = createSlotStatus(UUID.randomUUID(),
                URI.create("fake://bar/v1/agent/slot/slot2"),
                URI.create("fake://bar/v1/agent/slot/slot2"),
                "instance-id",
                "/location",
                STOPPED,
                APPLE_ASSIGNMENT,
                "/slot2",
                ImmutableMap.<String, Integer>of());
        AgentStatus agentStatus = new AgentStatus(UUID.randomUUID().toString(),
                ONLINE,
                "instance-id",
                URI.create("fake://foo/"),
                URI.create("fake://foo/"),
                "/unknown/location",
                "instance.type",
                ImmutableList.of(slot1, slot2),
                ImmutableMap.<String, Integer>of());
        provisioner.addAgents(agentStatus);
        coordinator.updateAllAgentsAndWait();

        int prefixSize = shortestUniquePrefix(asList(slot1.getId().toString(), slot2.getId().toString()), MIN_PREFIX_SIZE);

        URI requestUri = URI.create("http://localhost/v1/slot?host=foo");
        Response response = resource.getAllSlots(MockUriInfo.from(requestUri));
        assertEquals(response.getStatus(), Response.Status.OK.getStatusCode());
        assertEqualsNoOrder((Iterable<?>) response.getEntity(), ImmutableList.of(SlotStatusRepresentation.from(slot1, prefixSize, repository)));
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
            provisioner.addAgent(UUID.randomUUID().toString(), URI.create("fake://appleServer1/"), ImmutableMap.of("cpu", 8, "memory", 1024));
        }
        coordinator.updateAllAgentsAndWait();

        UriInfo uriInfo = MockUriInfo.from("http://localhost/v1/slot/assignment?host=apple*");
        Response response = resource.install(AssignmentRepresentation.from(assignment), limit, uriInfo, null);

        assertEquals(response.getStatus(), Response.Status.OK.getStatusCode());

        Collection<SlotStatusRepresentation> slots = (Collection<SlotStatusRepresentation>) response.getEntity();
        assertEquals(slots.size(), min(numberOfAgents, limit));
        for (SlotStatusRepresentation slotRepresentation : slots) {
            SlotStatus slot = slotRepresentation.toSlotStatus("instance");
            assertEquals(slot.getAssignment(), assignment);
            assertEquals(slot.getState(), STOPPED);
        }

        assertNull(response.getMetadata().get("Content-Type")); // content type is set by jersey based on @Produces
    }

    @Test
    public void testInstallWithinResourceLimit()
    {
        provisioner.addAgent(UUID.randomUUID().toString(), URI.create("fake://appleServer1/"), ImmutableMap.of("cpu", 1, "memory", 512));
        coordinator.updateAllAgentsAndWait();

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

    @Test(expectedExceptions = IllegalStateException.class, expectedExceptionsMessageRegExp = "No agents have the available resources to run the specified binary and configuration.")
    public void testInstallNotEnoughResources()
    {
        provisioner.addAgent(UUID.randomUUID().toString(), URI.create("fake://appleServer1/"), ImmutableMap.of("cpu", 0, "memory", 0));
        coordinator.updateAllAgentsAndWait();

        UriInfo uriInfo = MockUriInfo.from("http://localhost/v1/slot/assignment");
        Response response = resource.install(AssignmentRepresentation.from(APPLE_ASSIGNMENT), 1, uriInfo, null);

        assertEquals(response.getStatus(), Response.Status.OK.getStatusCode());

        response.getEntity();
    }

    @Test(expectedExceptions = IllegalStateException.class, expectedExceptionsMessageRegExp = "No agents have the available resources to run the specified binary and configuration.")
    public void testInstallResourcesConsumed()
    {
        provisioner.addAgent(UUID.randomUUID().toString(), URI.create("fake://appleServer1/"), ImmutableMap.of("cpu", 1, "memory", 512));
        coordinator.updateAllAgentsAndWait();

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
        response.getEntity();
    }

    private void assertAppleSlot(SlotStatusRepresentation slotRepresentation)
    {
        SlotStatus slot = slotRepresentation.toSlotStatus("instance");
        assertEquals(slot.getAssignment(), APPLE_ASSIGNMENT);
        assertEquals(slot.getState(), STOPPED);
        assertEquals(slot.getResources(), ImmutableMap.of("cpu", 1, "memory", 512));
    }
}
