package io.airlift.airship.coordinator;

import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import io.airlift.airship.shared.AgentLifecycleState;
import io.airlift.airship.shared.AgentStatus;
import io.airlift.airship.shared.CoordinatorLifecycleState;
import io.airlift.airship.shared.CoordinatorStatus;
import io.airlift.airship.shared.SlotStatus;
import io.airlift.units.Duration;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static io.airlift.airship.shared.AssignmentHelper.APPLE_ASSIGNMENT;
import static io.airlift.airship.shared.AssignmentHelper.BANANA_ASSIGNMENT;
import static io.airlift.airship.shared.AssignmentHelper.RESOLVED_APPLE_ASSIGNMENT;
import static io.airlift.airship.shared.AssignmentHelper.SHORT_APPLE_ASSIGNMENT;
import static io.airlift.airship.shared.SlotLifecycleState.STOPPED;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

public class TestCoordinator
{
    private Coordinator coordinator;
    private MockProvisioner provisioner;
    private TestingMavenRepository repository;
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
                "/local/location",
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
    }

    @AfterMethod
    public void tearDown()
            throws Exception
    {
        repository.destroy();
    }

    @Test
    public void testStatus()
            throws Exception
    {
        CoordinatorStatus actual = coordinator.status();
        assertEquals(actual.getCoordinatorId(), coordinatorStatus.getCoordinatorId());
        assertEquals(actual.getState(), CoordinatorLifecycleState.ONLINE);
        assertEquals(actual.getInstanceId(), coordinatorStatus.getInstanceId());
        assertEquals(actual.getLocation(), coordinatorStatus.getLocation());
        assertEquals(actual.getInstanceType(), coordinatorStatus.getInstanceType());
        assertEquals(actual.getInternalUri(), coordinatorStatus.getInternalUri());
        assertEquals(actual.getExternalUri(), coordinatorStatus.getExternalUri());
    }

    @Test
    public void testGetCoordinatorsDefault()
            throws Exception
    {
        // update the coordinator and verify
        assertEquals(coordinator.getCoordinators().size(), 1);
        CoordinatorStatus actual = coordinator.getCoordinators().get(0);
        assertEquals(actual.getCoordinatorId(), coordinatorStatus.getCoordinatorId());
        assertEquals(actual.getState(), CoordinatorLifecycleState.ONLINE);
        assertEquals(actual.getInstanceId(), coordinatorStatus.getInstanceId());
        assertEquals(actual.getLocation(), coordinatorStatus.getLocation());
        assertEquals(actual.getInstanceType(), coordinatorStatus.getInstanceType());
        assertEquals(actual.getInternalUri(), coordinatorStatus.getInternalUri());
        assertEquals(actual.getExternalUri(), coordinatorStatus.getExternalUri());
    }

    @Test
    public void testCoordinatorDiscovery()
            throws Exception
    {
        String coordinatorId = UUID.randomUUID().toString();
        String instanceId = "instance-id";
        URI internalUri = URI.create("fake://coordinator/" + instanceId + "/internal");
        URI externalUri = URI.create("fake://coordinator/" + instanceId + "/external");
        String location = "/unknown/location";
        String instanceType = "instance.type";

        // add the coordinator to the provisioner
        CoordinatorStatus status = new CoordinatorStatus(coordinatorId,
                CoordinatorLifecycleState.ONLINE,
                instanceId,
                internalUri,
                externalUri,
                location,
                instanceType);
        provisioner.addCoordinators(status);

        // coordinator won't see it until it update is called
        assertEquals(coordinator.getCoordinators().size(), 1);

        // update the coordinator
        coordinator.updateAllCoordinators();

        // locate the new coordinator
        List<CoordinatorStatus> coordinators = coordinator.getCoordinators();
        assertEquals(coordinators.size(), 2);
        CoordinatorStatus actual;
        if (coordinators.get(0).getInstanceId().equals(coordinatorStatus.getInstanceId())) {
            actual = coordinators.get(1);
        }
        else {
            actual = coordinators.get(0);
            assertEquals(coordinators.get(1).getInstanceId(), coordinatorStatus.getInstanceId());
        }

        // verify
        assertEquals(actual.getCoordinatorId(), coordinatorId);
        assertEquals(actual.getState(), CoordinatorLifecycleState.ONLINE);
        assertEquals(actual.getInstanceId(), instanceId);
        assertEquals(actual.getLocation(), location);
        assertEquals(actual.getInstanceType(), instanceType);
        assertEquals(actual.getInternalUri(), internalUri);
        assertEquals(actual.getExternalUri(), externalUri);
    }

    @Test
    public void testCoordinatorProvision()
            throws Exception
    {
        // provision the coordinator and verify
        String instanceType = "instance-type";
        List<CoordinatorStatus> coordinators = coordinator.provisionCoordinators("coordinator:config:1", 1, instanceType, null, null, null, null);
        assertNotNull(coordinators);
        assertEquals(coordinators.size(), 1);
        String instanceId = coordinators.get(0).getInstanceId();
        assertNotNull(instanceId);
        String location = coordinators.get(0).getLocation();
        assertNotNull(location);
        assertEquals(coordinators.get(0).getInstanceType(), instanceType);
        assertNull(coordinators.get(0).getCoordinatorId());
        assertNull(coordinators.get(0).getInternalUri());
        assertNull(coordinators.get(0).getExternalUri());
        assertEquals(coordinators.get(0).getState(), CoordinatorLifecycleState.PROVISIONING);

        // coordinator will initially report the coordinator as PROVISIONING
        assertEquals(coordinator.getCoordinators().size(), 2);
        assertEquals(coordinator.getCoordinator(instanceId).getInstanceId(), instanceId);
        assertEquals(coordinator.getCoordinator(instanceId).getInstanceType(), instanceType);
        assertEquals(coordinator.getCoordinator(instanceId).getLocation(), location);
        assertNull(coordinator.getCoordinator(instanceId).getCoordinatorId());
        assertNull(coordinator.getCoordinator(instanceId).getInternalUri());
        assertNull(coordinator.getCoordinator(instanceId).getExternalUri());
        assertEquals(coordinator.getCoordinator(instanceId).getState(), CoordinatorLifecycleState.PROVISIONING);

        // update coordinator, and verify the coordinator is still ONLINE (coordinators don't have live status like agents
        coordinator.updateAllCoordinators();
        assertEquals(coordinator.getCoordinators().size(), 2);
        assertEquals(coordinator.getCoordinator(instanceId).getInstanceId(), instanceId);
        assertEquals(coordinator.getCoordinator(instanceId).getInstanceType(), instanceType);
        assertEquals(coordinator.getCoordinator(instanceId).getLocation(), location);
        assertNull(coordinator.getCoordinator(instanceId).getCoordinatorId());
        assertNull(coordinator.getCoordinator(instanceId).getInternalUri());
        assertNull(coordinator.getCoordinator(instanceId).getExternalUri());
        assertEquals(coordinator.getCoordinator(instanceId).getState(), CoordinatorLifecycleState.PROVISIONING);

        // start and update the coordinator
        CoordinatorStatus expectedCoordinatorStatus = provisioner.startCoordinator(instanceId);
        coordinator.updateAllCoordinators();
        assertEquals(coordinator.getCoordinators().size(), 2);
        assertEquals(coordinator.getCoordinator(instanceId).getInstanceId(), instanceId);
        assertEquals(coordinator.getCoordinator(instanceId).getInstanceType(), instanceType);
        assertEquals(coordinator.getCoordinator(instanceId).getLocation(), location);
        assertEquals(coordinator.getCoordinator(instanceId).getCoordinatorId(), expectedCoordinatorStatus.getCoordinatorId());
        assertEquals(coordinator.getCoordinator(instanceId).getInternalUri(), expectedCoordinatorStatus.getInternalUri());
        assertEquals(coordinator.getCoordinator(instanceId).getExternalUri(), expectedCoordinatorStatus.getExternalUri());
        assertEquals(coordinator.getCoordinator(instanceId).getState(), CoordinatorLifecycleState.ONLINE);

        // update and verify nothing changed
        coordinator.updateAllCoordinators();
        assertEquals(coordinator.getCoordinators().size(), 2);
        assertEquals(coordinator.getCoordinator(instanceId).getInstanceId(), instanceId);
        assertEquals(coordinator.getCoordinator(instanceId).getInstanceType(), instanceType);
        assertEquals(coordinator.getCoordinator(instanceId).getLocation(), location);
        assertEquals(coordinator.getCoordinator(instanceId).getCoordinatorId(), expectedCoordinatorStatus.getCoordinatorId());
        assertEquals(coordinator.getCoordinator(instanceId).getInternalUri(), expectedCoordinatorStatus.getInternalUri());
        assertEquals(coordinator.getCoordinator(instanceId).getExternalUri(), expectedCoordinatorStatus.getExternalUri());
        assertEquals(coordinator.getCoordinator(instanceId).getState(), CoordinatorLifecycleState.ONLINE);
    }

    @Test
    public void testNoAgents()
            throws Exception
    {
        assertTrue(coordinator.getAgents().isEmpty());
    }

    @Test
    public void testAgentDiscovery()
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

        // add the agent to the provisioner
        provisioner.addAgents(status);

        // coordinator won't see it until it update is called
        assertTrue(coordinator.getAgents().isEmpty());

        // update the coordinator and verify
        coordinator.updateAllAgents();
        assertEquals(coordinator.getAgents(), ImmutableList.of(status));
        AgentStatus actual = coordinator.getAgents().get(0);
        assertEquals(actual.getAgentId(), agentId);
        assertEquals(actual.getState(), AgentLifecycleState.ONLINE);
        assertEquals(actual.getInstanceId(), instanceId);
        assertEquals(actual.getLocation(), location);
        assertEquals(actual.getInstanceType(), instanceType);
        assertEquals(actual.getInternalUri(), internalUri);
        assertEquals(actual.getExternalUri(), externalUri);
        assertEquals(actual.getResources(), resources);
    }

    @Test
    public void testAgentProvision()
            throws Exception
    {
        // provision the agent and verify
        String instanceType = "instance-type";
        List<AgentStatus> agents = coordinator.provisionAgents("agent:config:1", 1, instanceType, null, null, null, null);
        assertNotNull(agents);
        assertEquals(agents.size(), 1);
        String instanceId = agents.get(0).getInstanceId();
        assertNotNull(instanceId);
        String location = agents.get(0).getLocation();
        assertNotNull(location);
        assertEquals(agents.get(0).getInstanceType(), instanceType);
        assertNull(agents.get(0).getAgentId());
        assertNull(agents.get(0).getInternalUri());
        assertNull(agents.get(0).getExternalUri());
        assertEquals(agents.get(0).getState(), AgentLifecycleState.PROVISIONING);

        // coordinator will initially report the agent as PROVISIONING
        assertEquals(coordinator.getAgents().size(), 1);
        assertEquals(coordinator.getAgent(instanceId).getInstanceId(), instanceId);
        assertEquals(coordinator.getAgent(instanceId).getInstanceType(), instanceType);
        assertEquals(coordinator.getAgent(instanceId).getLocation(), location);
        assertNull(coordinator.getAgent(instanceId).getAgentId());
        assertNull(coordinator.getAgent(instanceId).getInternalUri());
        assertNull(coordinator.getAgent(instanceId).getExternalUri());
        assertEquals(coordinator.getAgent(instanceId).getState(), AgentLifecycleState.PROVISIONING);

        // update coordinator, and verify the agent is still PROVISIONING
        coordinator.updateAllAgents();
        assertEquals(coordinator.getAgents().size(), 1);
        assertEquals(coordinator.getAgent(instanceId).getInstanceId(), instanceId);
        assertEquals(coordinator.getAgent(instanceId).getInstanceType(), instanceType);
        assertEquals(coordinator.getAgent(instanceId).getLocation(), location);
        assertNull(coordinator.getAgent(instanceId).getAgentId());
        assertNull(coordinator.getAgent(instanceId).getInternalUri());
        assertNull(coordinator.getAgent(instanceId).getExternalUri());
        assertEquals(coordinator.getAgent(instanceId).getState(), AgentLifecycleState.PROVISIONING);

        // start the agent, update and verify
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

        // update and verify nothing changed
        coordinator.updateAllAgents();
        assertEquals(coordinator.getAgents().size(), 1);
        assertEquals(coordinator.getAgent(instanceId).getInstanceId(), instanceId);
        assertEquals(coordinator.getAgent(instanceId).getInstanceType(), instanceType);
        assertEquals(coordinator.getAgent(instanceId).getLocation(), location);
        assertEquals(coordinator.getAgent(instanceId).getAgentId(), expectedAgentStatus.getAgentId());
        assertEquals(coordinator.getAgent(instanceId).getInternalUri(), expectedAgentStatus.getInternalUri());
        assertEquals(coordinator.getAgent(instanceId).getExternalUri(), expectedAgentStatus.getExternalUri());
        assertEquals(coordinator.getAgent(instanceId).getState(), AgentLifecycleState.ONLINE);
    }

    @Test
    public void testInstallWithinShortBinarySpec()
    {
        URI agentUri = URI.create("fake://appleServer1/");
        provisioner.addAgent(UUID.randomUUID().toString(), agentUri, ImmutableMap.of("cpu", 1, "memory", 512));
        coordinator.updateAllAgents();

        List<SlotStatus> slots = coordinator.install(Predicates.<AgentStatus>alwaysTrue(), 1, SHORT_APPLE_ASSIGNMENT);

        assertEquals(slots.size(), 1);
        for (SlotStatus slot : slots) {
            assertAppleSlot(slot);
        }
    }

    @Test
    public void testInstallWithinResourceLimit()
    {
        URI agentUri = URI.create("fake://appleServer1/");

        provisioner.addAgent("instance-id", agentUri, ImmutableMap.of("cpu", 1, "memory", 512));
        coordinator.updateAllAgents();

        List<SlotStatus> slots = coordinator.install(Predicates.<AgentStatus>alwaysTrue(), 1, APPLE_ASSIGNMENT);

        assertEquals(slots.size(), 1);
        for (SlotStatus slot : slots) {
            assertAppleSlot(slot);
        }
    }

    @Test
    public void testInstallWithinUnlimitedResources()
    {
        URI agentUri = URI.create("fake://appleServer1/");

        provisioner.addAgent("instance-id", agentUri);
        coordinator.updateAllAgents();

        List<SlotStatus> slots = coordinator.install(Predicates.<AgentStatus>alwaysTrue(), 1, APPLE_ASSIGNMENT);

        assertEquals(slots.size(), 1);
        for (SlotStatus slot : slots) {
            assertAppleSlot(slot);
        }
    }

    @Test(expectedExceptions = IllegalStateException.class, expectedExceptionsMessageRegExp = "No agents have the available resources to run the specified binary and configuration.")
    public void testInstallNotEnoughResources()
    {
        URI agentUri = URI.create("fake://appleServer1/");

        provisioner.addAgent("instance-id", agentUri, ImmutableMap.of("cpu", 0, "memory", 0));
        coordinator.updateAllAgents();

        coordinator.install(Predicates.<AgentStatus>alwaysTrue(), 1, APPLE_ASSIGNMENT);
    }

    @Test(expectedExceptions = IllegalStateException.class, expectedExceptionsMessageRegExp = "No agents have the available resources to run the specified binary and configuration.")
    public void testInstallResourcesConsumed()
    {
        URI agentUri = URI.create("fake://appleServer1/");
        provisioner.addAgent("instance-id", agentUri, ImmutableMap.of("cpu", 1, "memory", 512));
        coordinator.updateAllAgents();

        // install an apple server
        List<SlotStatus> slots = coordinator.install(Predicates.<AgentStatus>alwaysTrue(), 1, APPLE_ASSIGNMENT);
        assertEquals(slots.size(), 1);
        assertAppleSlot(Iterables.get(slots, 0));

        // try to install a banana server which will fail
        coordinator.install(Predicates.<AgentStatus>alwaysTrue(), 1, BANANA_ASSIGNMENT);
    }

    private void assertAppleSlot(SlotStatus slot)
    {
        assertEquals(slot.getAssignment(), RESOLVED_APPLE_ASSIGNMENT);
        assertEquals(slot.getState(), STOPPED);
        assertEquals(slot.getResources(), ImmutableMap.of("cpu", 1, "memory", 512));
    }
}
