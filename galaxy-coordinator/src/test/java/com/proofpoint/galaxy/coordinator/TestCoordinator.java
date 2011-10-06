package com.proofpoint.galaxy.coordinator;

import com.google.common.collect.ImmutableList;
import com.proofpoint.galaxy.shared.AgentLifecycleState;
import com.proofpoint.galaxy.shared.AgentStatus;
import com.proofpoint.galaxy.shared.SlotStatus;
import com.proofpoint.http.server.HttpServerConfig;
import com.proofpoint.http.server.HttpServerInfo;
import com.proofpoint.node.NodeInfo;
import com.proofpoint.units.Duration;
import junit.framework.TestCase;

import java.net.URI;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static com.proofpoint.galaxy.coordinator.RepoHelper.MOCK_BINARY_REPO;
import static com.proofpoint.galaxy.coordinator.RepoHelper.MOCK_CONFIG_REPO;

public class TestCoordinator extends TestCase
{

    private Coordinator coordinator;
    private Duration statusExpiration = new Duration(500, TimeUnit.MILLISECONDS);
    private LocalProvisioner provisioner;

    @Override
    public void setUp()
            throws Exception
    {
        BinaryUrlResolver urlResolver = new BinaryUrlResolver(MOCK_BINARY_REPO, new HttpServerInfo(new HttpServerConfig(), new NodeInfo("testing")));

        provisioner = new LocalProvisioner();
        coordinator = new Coordinator(new MockRemoteAgentFactory(),
                urlResolver,
                MOCK_CONFIG_REPO,
                new LocalConfigRepository(new NodeInfo("test"), new CoordinatorConfig(), null),
                provisioner,
                statusExpiration
        );

    }

    public void testNoAgents()
            throws Exception
    {
        assertTrue(coordinator.getAllAgentStatus().isEmpty());
    }

    public void testOneAgent()
            throws Exception
    {
        String agentId = UUID.randomUUID().toString();
        URI agentUri = URI.create("fake://agent/" + agentId);

        AgentStatus status = new AgentStatus(agentId, AgentLifecycleState.ONLINE, agentUri, "unknown/location", "instance.type", ImmutableList.<SlotStatus>of());
        coordinator.setAgentStatus(status);

        assertEquals(coordinator.getAllAgentStatus(), ImmutableList.of(status));
        assertEquals(coordinator.getAgentStatus(agentId).getAgentId(), agentId);
        assertEquals(coordinator.getAgentStatus(agentId).getState(), AgentLifecycleState.ONLINE);
    }

    public void testAgentProvision()
            throws Exception
    {
        String agentId = UUID.randomUUID().toString();
        URI agentUri = URI.create("fake://agent/" + agentId);

        // provision the agent
        provisioner.addAgent(new Ec2Location("region", "zone", agentId, "agent", agentUri));

        // coordinator won't see it until it update is called
        assertTrue(coordinator.getAllAgentStatus().isEmpty());

        // announce the new agent and verify
        coordinator.updateAllAgentsStatus();
        assertEquals(coordinator.getAgentStatus(agentId).getAgentId(), agentId);
        assertEquals(coordinator.getAgentStatus(agentId).getState(), AgentLifecycleState.ONLINE);

        // remove the slot from provisioner and coordinator remember it
        provisioner.removeAgent(agentId);
        assertEquals(coordinator.getAgentStatus(agentId).getAgentId(), agentId);
        // slot is online because MOCK slot is fake.... a real slot would transition to offline
        assertEquals(coordinator.getAgentStatus(agentId).getState(), AgentLifecycleState.ONLINE);
    }
}
