package com.proofpoint.galaxy.coordinator;

import java.util.UUID;

public class MockRemoteAgentFactory implements RemoteAgentFactory
{
    @Override
    public RemoteAgent createRemoteAgent(UUID agentId)
    {
        return new MockRemoteAgent(agentId);
    }
}
