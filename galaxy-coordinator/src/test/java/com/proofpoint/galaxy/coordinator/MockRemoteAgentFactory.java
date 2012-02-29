package com.proofpoint.galaxy.coordinator;

import com.proofpoint.galaxy.shared.AgentStatus;

public class MockRemoteAgentFactory implements RemoteAgentFactory
{
    @Override
    public RemoteAgent createRemoteAgent(AgentStatus agentStatus)
    {
        return new MockRemoteAgent(agentStatus);
    }
}
