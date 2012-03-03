package com.proofpoint.galaxy.coordinator;

import com.google.common.base.Preconditions;
import com.proofpoint.galaxy.shared.AgentStatus;

import java.util.Map;

public class MockRemoteAgentFactory implements RemoteAgentFactory
{
    private final Map<String, AgentStatus> agents;

    public MockRemoteAgentFactory(Map<String, AgentStatus> agents)
    {
        Preconditions.checkNotNull(agents, "agents is null");
        this.agents = agents;
    }

    @Override
    public RemoteAgent createRemoteAgent(Instance instance)
    {
        Preconditions.checkArgument(agents.containsKey(instance.getInstanceId()), "Unknown instance %s", instance.getInstanceId());
        return new MockRemoteAgent(instance.getInstanceId(), agents);
    }
}
