package com.proofpoint.galaxy.coordinator;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.proofpoint.galaxy.shared.AgentLifecycleState;
import com.proofpoint.galaxy.shared.AgentStatus;
import com.proofpoint.galaxy.shared.AgentStatusRepresentation;
import com.proofpoint.galaxy.shared.MockUriInfo;
import com.proofpoint.galaxy.shared.SlotStatus;
import com.proofpoint.node.NodeInfo;
import com.proofpoint.units.Duration;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import javax.ws.rs.core.Response;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static com.proofpoint.galaxy.shared.ExtraAssertions.assertEqualsNoOrder;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;

public class TestAdminResource
{
    private AdminResource resource;
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
                new CoordinatorConfig().setStatusExpiration(new Duration(1, TimeUnit.DAYS)),
                provisioner.getAgentFactory(),
                repository,
                provisioner,
                new InMemoryStateManager(),
                new MockServiceInventory());
        resource = new AdminResource(coordinator, repository);
    }

    @AfterMethod
    public void tearDown()
            throws Exception
    {
        repository.destroy();
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
        coordinator.updateAllAgents();

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
                new AgentProvisioningRepresentation("agent:config:1", 1, instanceType, null, null, null, null),
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
        coordinator.updateAllAgents();
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
