package io.airlift.airship.coordinator;

import com.google.common.base.Preconditions;
import io.airlift.airship.shared.AgentLifecycleState;
import io.airlift.airship.shared.AgentStatus;

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
    public RemoteAgent createRemoteAgent(Instance instance, AgentLifecycleState state)
    {
        AgentStatus agentStatus = agents.get(instance.getInstanceId());
        Preconditions.checkArgument(agentStatus != null, "Unknown instance %s", instance.getInstanceId());
        return new MockRemoteAgent(instance.getInstanceId(), agents);
    }
}
