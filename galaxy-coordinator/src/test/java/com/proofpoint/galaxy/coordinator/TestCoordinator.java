package com.proofpoint.galaxy.coordinator;

import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.proofpoint.galaxy.shared.AgentLifecycleState;
import com.proofpoint.galaxy.shared.AgentStatus;
import com.proofpoint.galaxy.shared.SlotStatus;
import com.proofpoint.node.NodeInfo;
import com.proofpoint.units.Duration;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static com.proofpoint.galaxy.shared.AssignmentHelper.APPLE_ASSIGNMENT;
import static com.proofpoint.galaxy.shared.AssignmentHelper.BANANA_ASSIGNMENT;
import static com.proofpoint.galaxy.shared.AssignmentHelper.RESOLVED_APPLE_ASSIGNMENT;
import static com.proofpoint.galaxy.shared.AssignmentHelper.SHORT_APPLE_ASSIGNMENT;
import static com.proofpoint.galaxy.shared.SlotLifecycleState.STOPPED;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

public class TestCoordinator
{
    private Coordinator coordinator;
    private Duration statusExpiration = new Duration(500, TimeUnit.MILLISECONDS);
    private MockProvisioner provisioner;
    private TestingMavenRepository repository;

    @BeforeMethod
    public void setUp()
            throws Exception
    {
        NodeInfo nodeInfo = new NodeInfo("testing");

        repository = new TestingMavenRepository();

        provisioner = new MockProvisioner();
        coordinator = new Coordinator(nodeInfo.getEnvironment(),
                provisioner.getAgentFactory(),
                repository,
                provisioner,
                new InMemoryStateManager(),
                new MockServiceInventory(),
                statusExpiration,
                false);
    }

    @AfterMethod
    public void tearDown()
            throws Exception
    {
        repository.destroy();
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

        // start the agent, but don't update
        AgentStatus expectedAgentStatus = provisioner.startAgent(instanceId);
        assertEquals(coordinator.getAgents().size(), 1);
        assertEquals(coordinator.getAgent(instanceId).getInstanceType(), instanceType);
        assertEquals(coordinator.getAgent(instanceId).getLocation(), location);
        assertEquals(coordinator.getAgent(instanceId).getInstanceId(), instanceId);
        assertNull(coordinator.getAgent(instanceId).getAgentId());
        assertNull(coordinator.getAgent(instanceId).getInternalUri());
        assertNull(coordinator.getAgent(instanceId).getExternalUri());
        assertEquals(coordinator.getAgent(instanceId).getState(), AgentLifecycleState.PROVISIONING);

        // update and verify
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
    public void testInstallNotEnoughResources()
    {
        URI agentUri = URI.create("fake://appleServer1/");

        provisioner.addAgent("instance-id", agentUri);
        coordinator.updateAllAgents();

        List<SlotStatus> slots = coordinator.install(Predicates.<AgentStatus>alwaysTrue(), 1, APPLE_ASSIGNMENT);
        assertEquals(slots.size(), 0);
    }

    @Test
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
        slots = coordinator.install(Predicates.<AgentStatus>alwaysTrue(), 1, BANANA_ASSIGNMENT);
        assertEquals(slots.size(), 0);
    }

    private void assertAppleSlot(SlotStatus slot)
    {
        assertEquals(slot.getAssignment(), RESOLVED_APPLE_ASSIGNMENT);
        assertEquals(slot.getState(), STOPPED);
        assertEquals(slot.getResources(), ImmutableMap.of("cpu", 1, "memory", 512));
    }
}
