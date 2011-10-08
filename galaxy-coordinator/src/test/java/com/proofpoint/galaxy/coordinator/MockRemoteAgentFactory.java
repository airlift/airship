package com.proofpoint.galaxy.coordinator;

import java.net.URI;

public class MockRemoteAgentFactory implements RemoteAgentFactory
{
    @Override
    public RemoteAgent createRemoteAgent(String agentId, String instanceType, URI uri)
    {
        return new MockRemoteAgent(agentId, instanceType, uri);
    }
}
