package com.proofpoint.galaxy.coordinator;

import com.google.common.base.Joiner;
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
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static com.proofpoint.galaxy.shared.AgentLifecycleState.ONLINE;
import static com.proofpoint.galaxy.shared.AssignmentHelper.APPLE_ASSIGNMENT;
import static com.proofpoint.galaxy.shared.AssignmentHelper.BANANA_ASSIGNMENT;
import static com.proofpoint.galaxy.shared.AssignmentHelper.RESOLVED_APPLE_ASSIGNMENT;
import static com.proofpoint.galaxy.shared.AssignmentHelper.SHORT_APPLE_ASSIGNMENT;
import static com.proofpoint.galaxy.shared.SlotLifecycleState.STOPPED;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

public class TestCoordinator
{

    private Coordinator coordinator;
    private Duration statusExpiration = new Duration(500, TimeUnit.MILLISECONDS);
    private LocalProvisioner provisioner;
    private TestingRepository repository;

    @BeforeMethod
    public void setUp()
            throws Exception
    {
        NodeInfo nodeInfo = new NodeInfo("testing");

        repository = new TestingRepository();

        provisioner = new LocalProvisioner();
        coordinator = new Coordinator(nodeInfo.getEnvironment(),
                new MockRemoteAgentFactory(),
                repository,
                provisioner,
                new InMemoryStateManager(),
                new MockServiceInventory(),
                statusExpiration
        );

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
        assertTrue(coordinator.getAllAgentStatus().isEmpty());
    }

    @Test
    public void testOneAgent()
            throws Exception
    {
        String agentId = UUID.randomUUID().toString();
        URI agentUri = URI.create("fake://agent/" + agentId);

        AgentStatus status = new AgentStatus(agentId,
                AgentLifecycleState.ONLINE,
                agentUri,
                "unknown/location",
                "instance.type",
                ImmutableList.<SlotStatus>of(),
                ImmutableMap.of("cpu", 8, "memory", 1024));
        coordinator.setAgentStatus(status);

        assertEquals(coordinator.getAllAgentStatus(), ImmutableList.of(status));
        assertEquals(coordinator.getAgentStatus(agentId).getAgentId(), agentId);
        assertEquals(coordinator.getAgentStatus(agentId).getState(), AgentLifecycleState.ONLINE);
    }

    @Test
    public void testAgentProvision()
            throws Exception
    {
        String agentId = UUID.randomUUID().toString();
        URI agentUri = URI.create("fake://agent/" + agentId);

        // provision the agent
        provisioner.addAgent(new Instance(agentId, "test.type", Joiner.on('/').join("ec2", "region", "zone", agentId, "agent"), agentUri));

        // coordinator won't see it until it update is called
        assertTrue(coordinator.getAllAgentStatus().isEmpty());

        // announce the new agent and verify
        coordinator.updateAllAgents();
        assertEquals(coordinator.getAgentStatus(agentId).getAgentId(), agentId);
        assertEquals(coordinator.getAgentStatus(agentId).getState(), AgentLifecycleState.ONLINE);

        // remove the slot from provisioner and coordinator remember it
        provisioner.removeAgent(agentId);
        assertEquals(coordinator.getAgentStatus(agentId).getAgentId(), agentId);
        // slot is online because MOCK slot is fake.... a real slot would transition to offline
        assertEquals(coordinator.getAgentStatus(agentId).getState(), AgentLifecycleState.ONLINE);
    }

    @Test
    public void testInstallWithinShortBinarySpec()
    {
        final AgentStatus status = new AgentStatus(UUID.randomUUID().toString(),
                ONLINE,
                URI.create("fake://appleServer1/"),
                "unknown/location",
                "instance.type",
                ImmutableList.<SlotStatus>of(),
                ImmutableMap.of("cpu", 1, "memory", 512));
        coordinator.setAgentStatus(status);

        List<SlotStatus> slots = coordinator.install(Predicates.<AgentStatus>alwaysTrue(), 1, SHORT_APPLE_ASSIGNMENT);

        assertEquals(slots.size(), 1);
        for (SlotStatus slot : slots) {
            assertAppleSlot(slot);
        }
    }

    @Test
    public void testInstallWithinResourceLimit()
    {
        final AgentStatus status = new AgentStatus(UUID.randomUUID().toString(),
                ONLINE,
                URI.create("fake://appleServer1/"),
                "unknown/location",
                "instance.type",
                ImmutableList.<SlotStatus>of(),
                ImmutableMap.of("cpu", 1, "memory", 512));
        coordinator.setAgentStatus(status);

        List<SlotStatus> slots = coordinator.install(Predicates.<AgentStatus>alwaysTrue(), 1, APPLE_ASSIGNMENT);

        assertEquals(slots.size(), 1);
        for (SlotStatus slot : slots) {
            assertAppleSlot(slot);
        }
    }

    @Test
    public void testInstallNotEnoughResources()
    {
        final AgentStatus status = new AgentStatus(UUID.randomUUID().toString(),
                ONLINE,
                URI.create("fake://appleServer1/"),
                "unknown/location",
                "instance.type",
                ImmutableList.<SlotStatus>of(),
                ImmutableMap.<String, Integer>of());
        coordinator.setAgentStatus(status);

        List<SlotStatus> slots = coordinator.install(Predicates.<AgentStatus>alwaysTrue(), 1, APPLE_ASSIGNMENT);
        assertEquals(slots.size(), 0);
    }

    @Test
    public void testInstallResourcesConsumed()
    {
        final AgentStatus status = new AgentStatus(UUID.randomUUID().toString(),
                ONLINE,
                URI.create("fake://appleServer1/"),
                "unknown/location",
                "instance.type",
                ImmutableList.<SlotStatus>of(),
                ImmutableMap.of("cpu", 1, "memory", 512));
        coordinator.setAgentStatus(status);

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
