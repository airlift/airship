package com.proofpoint.galaxy.coordinator;

import com.google.common.collect.ImmutableList;
import com.google.common.testing.FakeTicker;
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
    private FakeTicker fakeTicker = new FakeTicker();
    private Duration statusExpiration = new Duration(5, TimeUnit.MILLISECONDS);

    @Override
    public void setUp()
            throws Exception
    {
        BinaryUrlResolver urlResolver = new BinaryUrlResolver(MOCK_BINARY_REPO, new HttpServerInfo(new HttpServerConfig(), new NodeInfo("testing")));

        coordinator = new Coordinator(new MockRemoteAgentFactory(fakeTicker),
                urlResolver,
                MOCK_CONFIG_REPO,
                new LocalConfigRepository(new CoordinatorConfig(), null),
                statusExpiration,
                fakeTicker
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
        UUID agentId = UUID.randomUUID();
        URI agentUri = URI.create("fake://agent/" + agentId);

        AgentStatus status = new AgentStatus(agentId, AgentLifecycleState.ONLINE, agentUri, ImmutableList.<SlotStatus>of());
        coordinator.updateAgentStatus(status);

        assertEquals(coordinator.getAllAgentStatus(), ImmutableList.of(status));
        assertEquals(coordinator.getAgentStatus(agentId).getAgentId(), agentId);
        assertEquals(coordinator.getAgentStatus(agentId).getState(), AgentLifecycleState.ONLINE);
    }

    public void testAgentTimeout()
            throws Exception
    {
        UUID agentId = UUID.randomUUID();
        URI agentUri = URI.create("fake://agent/" + agentId);

        assertTrue(coordinator.getAllAgentStatus().isEmpty());

        // announce the new agent and verify
        AgentStatus status = new AgentStatus(agentId, AgentLifecycleState.ONLINE, agentUri, ImmutableList.<SlotStatus>of());
        coordinator.updateAgentStatus(status);

        assertEquals(coordinator.getAllAgentStatus(), ImmutableList.of(status));
        assertEquals(coordinator.getAgentStatus(agentId).getAgentId(), agentId);
        assertEquals(coordinator.getAgentStatus(agentId).getState(), AgentLifecycleState.ONLINE);

        // force timeout byt advancing past the timeout
        fakeTicker.advance((long) (statusExpiration.convertTo(TimeUnit.NANOSECONDS) * 2));
        coordinator.checkAgentStatuses();

        // verify server is offline
        assertEquals(coordinator.getAgentStatus(agentId).getAgentId(), agentId);
        assertEquals(coordinator.getAgentStatus(agentId).getState(), AgentLifecycleState.OFFLINE);
        // this works because equals is based solely on agentId
        assertEquals(coordinator.getAllAgentStatus(), ImmutableList.of(status));

    }
}
