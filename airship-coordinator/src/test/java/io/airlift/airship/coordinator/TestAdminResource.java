package io.airlift.airship.coordinator;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import io.airlift.airship.shared.AgentLifecycleState;
import io.airlift.airship.shared.AgentStatus;
import io.airlift.airship.shared.AgentStatusRepresentation;
import io.airlift.airship.shared.CoordinatorLifecycleState;
import io.airlift.airship.shared.CoordinatorStatus;
import io.airlift.airship.shared.CoordinatorStatusRepresentation;
import io.airlift.airship.shared.MockUriInfo;
import io.airlift.airship.shared.SlotStatus;
import io.airlift.units.Duration;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import javax.ws.rs.core.Response;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static io.airlift.airship.shared.ExtraAssertions.assertEqualsNoOrder;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;

public class TestAdminResource
{
    private AdminResource resource;
    private Coordinator coordinator;
    private TestingMavenRepository repository;
    private MockProvisioner provisioner;
    private CoordinatorStatus coordinatorStatus;

    @BeforeMethod
    public void setUp()
            throws Exception
    {
        coordinatorStatus = new CoordinatorStatus(UUID.randomUUID().toString(),
                CoordinatorLifecycleState.ONLINE,
                "this-coordinator-instance-id",
                URI.create("fake://coordinator/internal"),
                URI.create("fake://coordinator/external"),
                "/test/location",
                "this-coordinator-instance-type");

        repository = new TestingMavenRepository();

        provisioner = new MockProvisioner();
        coordinator = new Coordinator(coordinatorStatus,
                provisioner.getCoordinatorFactory(),
                provisioner.getAgentFactory(),
                repository,
                provisioner,
                new InMemoryStateManager(),
                new MockServiceInventory(),
                new Duration(1, TimeUnit.DAYS),
                false);
        resource = new AdminResource(coordinator, repository);
    }

    @AfterMethod
    public void tearDown()
            throws Exception
    {
        repository.destroy();
    }

    @Test
    public void testGetCoordinatorsDefault()
    {
        URI requestUri = URI.create("http://localhost/v1/admin/coordinator");
        Response response = resource.getAllCoordinators(MockUriInfo.from(requestUri));
        assertEquals(response.getStatus(), Response.Status.OK.getStatusCode());
        assertNull(response.getMetadata().get("Content-Type")); // content type is set by jersey based on @Produces

        Iterable<CoordinatorStatusRepresentation> coordinators = (Iterable<CoordinatorStatusRepresentation>) response.getEntity();
        assertEquals(Iterables.size(coordinators), 1);
        CoordinatorStatusRepresentation actual = coordinators.iterator().next();
        assertEquals(actual.getCoordinatorId(), coordinatorStatus.getCoordinatorId());
        assertEquals(actual.getState(), CoordinatorLifecycleState.ONLINE);
        assertEquals(actual.getInstanceId(), coordinatorStatus.getInstanceId());
        assertEquals(actual.getLocation(), coordinatorStatus.getLocation());
        assertEquals(actual.getInstanceType(), coordinatorStatus.getInstanceType());
        assertEquals(actual.getSelf(), coordinatorStatus.getInternalUri());
        assertEquals(actual.getExternalUri(), coordinatorStatus.getExternalUri());
    }

    @Test
    public void testGetAllCoordinatorsSingle()
            throws Exception
    {
        String coordinatorId = UUID.randomUUID().toString();
        String instanceId = "instance-id";
        URI internalUri = URI.create("fake://coordinator/" + instanceId + "/internal");
        URI externalUri = URI.create("fake://coordinator/" + instanceId + "/external");
        String location = "/unknown/location";
        String instanceType = "instance.type";

        CoordinatorStatus status = new CoordinatorStatus(coordinatorId,
                CoordinatorLifecycleState.ONLINE,
                instanceId,
                internalUri,
                externalUri,
                location,
                instanceType);

        // add the coordinator
        provisioner.addCoordinators(status);
        coordinator.updateAllCoordinatorsAndWait();

        URI requestUri = URI.create("http://localhost/v1/admin/coordinator");
        Response response = resource.getAllCoordinators(MockUriInfo.from(requestUri));
        assertEquals(response.getStatus(), Response.Status.OK.getStatusCode());
        assertNull(response.getMetadata().get("Content-Type")); // content type is set by jersey based on @Produces

        // locate the new coordinator
        List<CoordinatorStatusRepresentation> coordinators = ImmutableList.copyOf((Iterable<CoordinatorStatusRepresentation>) response.getEntity());
        CoordinatorStatusRepresentation actual = getNonMainCoordinator(coordinators);

        assertEquals(actual.getCoordinatorId(), coordinatorId);
        assertEquals(actual.getState(), CoordinatorLifecycleState.ONLINE);
        assertEquals(actual.getInstanceId(), instanceId);
        assertEquals(actual.getLocation(), location);
        assertEquals(actual.getInstanceType(), instanceType);
        assertEquals(actual.getSelf(), internalUri);
        assertEquals(actual.getExternalUri(), externalUri);
    }

    private CoordinatorStatusRepresentation getNonMainCoordinator(List<CoordinatorStatusRepresentation> coordinators)
    {
        assertEquals(coordinators.size(), 2);
        CoordinatorStatusRepresentation actual;
        if (coordinators.get(0).getInstanceId().equals(coordinatorStatus.getInstanceId())) {
            actual = coordinators.get(1);
        }
        else {
            actual = coordinators.get(0);
            assertEquals(coordinators.get(1).getInstanceId(), coordinatorStatus.getInstanceId());
        }
        return actual;
    }

    @Test
    public void testCoordinatorProvision()
            throws Exception
    {
        // provision the coordinator and verify
        String instanceType = "instance-type";
        URI requestUri = URI.create("http://localhost/v1/admin/coordinator");
        Response response = resource.provisionCoordinator(
                new CoordinatorProvisioningRepresentation("coordinator:config:1", 1, instanceType, null, null, null, null, null),
                MockUriInfo.from(requestUri)
        );
        assertEquals(response.getStatus(), Response.Status.OK.getStatusCode());
        assertNull(response.getMetadata().get("Content-Type")); // content type is set by jersey based on @Produces

        List<CoordinatorStatusRepresentation> coordinators = ImmutableList.copyOf((Iterable<CoordinatorStatusRepresentation>) response.getEntity());
        assertEquals(coordinators.size(), 1);
        String instanceId = coordinators.get(0).getInstanceId();
        assertNotNull(instanceId);
        String location = coordinators.get(0).getLocation();
        assertNotNull(location);
        assertEquals(coordinators.get(0).getInstanceType(), instanceType);
        assertNull(coordinators.get(0).getCoordinatorId());
        assertNull(coordinators.get(0).getSelf());
        assertNull(coordinators.get(0).getExternalUri());
        assertEquals(coordinators.get(0).getState(), CoordinatorLifecycleState.PROVISIONING);

        // start the coordinator and verify
        CoordinatorStatus coordinatorStatus = provisioner.startCoordinator(instanceId);
        coordinator.updateAllCoordinatorsAndWait();
        assertEquals(coordinator.getCoordinators().size(), 2);
        assertEquals(coordinator.getCoordinator(instanceId).getInstanceId(), instanceId);
        assertEquals(coordinator.getCoordinator(instanceId).getInstanceType(), instanceType);
        assertEquals(coordinator.getCoordinator(instanceId).getLocation(), location);
        assertEquals(coordinator.getCoordinator(instanceId).getCoordinatorId(), coordinatorStatus.getCoordinatorId());
        assertEquals(coordinator.getCoordinator(instanceId).getInternalUri(), coordinatorStatus.getInternalUri());
        assertEquals(coordinator.getCoordinator(instanceId).getExternalUri(), coordinatorStatus.getExternalUri());
        assertEquals(coordinator.getCoordinator(instanceId).getState(), CoordinatorLifecycleState.ONLINE);


        requestUri = URI.create("http://localhost/v1/admin/coordinator");
        response = resource.getAllCoordinators(MockUriInfo.from(requestUri));
        assertEquals(response.getStatus(), Response.Status.OK.getStatusCode());
        assertNull(response.getMetadata().get("Content-Type")); // content type is set by jersey based on @Produces

        coordinators = ImmutableList.copyOf((Iterable<CoordinatorStatusRepresentation>) response.getEntity());
        CoordinatorStatusRepresentation actual = getNonMainCoordinator(coordinators);

        assertEquals(actual.getInstanceId(), instanceId);
        assertEquals(actual.getInstanceType(), instanceType);
        assertEquals(actual.getLocation(), location);
        assertEquals(actual.getCoordinatorId(), coordinatorStatus.getCoordinatorId());
        assertEquals(actual.getSelf(), coordinatorStatus.getInternalUri());
        assertEquals(actual.getExternalUri(), coordinatorStatus.getExternalUri());
        assertEquals(actual.getState(), CoordinatorLifecycleState.ONLINE);
    }

    @Test
    public void testGetAllAgentsEmpty()
    {
        URI requestUri = URI.create("http://localhost/v1/admin/agent");
        Response response = resource.getAllAgents(MockUriInfo.from(requestUri));
        assertEquals(response.getStatus(), Response.Status.OK.getStatusCode());
        assertEqualsNoOrder((Iterable<?>) response.getEntity(), ImmutableList.of());
        assertNull(response.getMetadata().get("Content-Type")); // content type is set by jersey based on @Produces
    }

    @Test
    public void testGetAllAgentsSingle()
            throws Exception
    {
        String agentId = UUID.randomUUID().toString();
        URI internalUri = URI.create("fake://agent/" + agentId + "/internal");
        URI externalUri = URI.create("fake://agent/" + agentId + "/external");
        String instanceId = "instance-id";
        String location = "/unknown/location";
        String instanceType = "instance.type";
        Map<String, Integer> resources = ImmutableMap.of("cpu", 8, "memory", 1024);

        AgentStatus status = new AgentStatus(agentId,
                AgentLifecycleState.ONLINE,
                instanceId,
                internalUri,
                externalUri,
                location,
                instanceType,
                ImmutableList.<SlotStatus>of(),
                resources);

        // add the agent
        provisioner.addAgents(status);
        coordinator.updateAllAgentsAndWait();

        URI requestUri = URI.create("http://localhost/v1/admin/agent");
        Response response = resource.getAllAgents(MockUriInfo.from(requestUri));
        assertEquals(response.getStatus(), Response.Status.OK.getStatusCode());
        assertNull(response.getMetadata().get("Content-Type")); // content type is set by jersey based on @Produces

        Iterable<AgentStatusRepresentation> agents = (Iterable<AgentStatusRepresentation>) response.getEntity();
        assertEquals(Iterables.size(agents), 1);
        AgentStatusRepresentation actual = agents.iterator().next();

        assertEquals(actual.getAgentId(), agentId);
        assertEquals(actual.getState(), AgentLifecycleState.ONLINE);
        assertEquals(actual.getInstanceId(), instanceId);
        assertEquals(actual.getLocation(), location);
        assertEquals(actual.getInstanceType(), instanceType);
        assertEquals(actual.getSelf(), internalUri);
        assertEquals(actual.getExternalUri(), externalUri);
        assertEquals(actual.getResources(), resources);
    }

    @Test
    public void testAgentProvision()
            throws Exception
    {
        // provision the agent and verify
        String instanceType = "instance-type";
        URI requestUri = URI.create("http://localhost/v1/admin/agent");
        Response response = resource.provisionAgent(
                new AgentProvisioningRepresentation("agent:config:1", 1, instanceType, null, null, null, null, null),
                MockUriInfo.from(requestUri)
        );
        assertEquals(response.getStatus(), Response.Status.OK.getStatusCode());
        assertNull(response.getMetadata().get("Content-Type")); // content type is set by jersey based on @Produces

        List<AgentStatusRepresentation> agents = ImmutableList.copyOf((Iterable<AgentStatusRepresentation>) response.getEntity());
        assertEquals(agents.size(), 1);
        String instanceId = agents.get(0).getInstanceId();
        assertNotNull(instanceId);
        String location = agents.get(0).getLocation();
        assertNotNull(location);
        assertEquals(agents.get(0).getInstanceType(), instanceType);
        assertNull(agents.get(0).getAgentId());
        assertNull(agents.get(0).getSelf());
        assertNull(agents.get(0).getExternalUri());
        assertEquals(agents.get(0).getState(), AgentLifecycleState.PROVISIONING);

        // start the agent and verify
        AgentStatus expectedAgentStatus = provisioner.startAgent(instanceId);
        coordinator.updateAllAgentsAndWait();
        assertEquals(coordinator.getAgents().size(), 1);
        assertEquals(coordinator.getAgent(instanceId).getInstanceId(), instanceId);
        assertEquals(coordinator.getAgent(instanceId).getInstanceType(), instanceType);
        assertEquals(coordinator.getAgent(instanceId).getLocation(), location);
        assertEquals(coordinator.getAgent(instanceId).getAgentId(), expectedAgentStatus.getAgentId());
        assertEquals(coordinator.getAgent(instanceId).getInternalUri(), expectedAgentStatus.getInternalUri());
        assertEquals(coordinator.getAgent(instanceId).getExternalUri(), expectedAgentStatus.getExternalUri());
        assertEquals(coordinator.getAgent(instanceId).getState(), AgentLifecycleState.ONLINE);


        requestUri = URI.create("http://localhost/v1/admin/agent");
        response = resource.getAllAgents(MockUriInfo.from(requestUri));
        assertEquals(response.getStatus(), Response.Status.OK.getStatusCode());
        assertNull(response.getMetadata().get("Content-Type")); // content type is set by jersey based on @Produces

        agents = ImmutableList.copyOf((Iterable<AgentStatusRepresentation>) response.getEntity());
        assertEquals(agents.size(), 1);
        AgentStatusRepresentation actual = agents.iterator().next();

        assertEquals(actual.getInstanceId(), instanceId);
        assertEquals(actual.getInstanceType(), instanceType);
        assertEquals(actual.getLocation(), location);
        assertEquals(actual.getAgentId(), expectedAgentStatus.getAgentId());
        assertEquals(actual.getSelf(), expectedAgentStatus.getInternalUri());
        assertEquals(actual.getExternalUri(), expectedAgentStatus.getExternalUri());
        assertEquals(actual.getState(), AgentLifecycleState.ONLINE);
    }
}
