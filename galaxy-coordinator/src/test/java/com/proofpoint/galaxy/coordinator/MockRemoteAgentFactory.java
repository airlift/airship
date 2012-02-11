package com.proofpoint.galaxy.coordinator;

import java.net.URI;

public class MockRemoteAgentFactory implements RemoteAgentFactory
{
    @Override
    public RemoteAgent createRemoteAgent(String agentId, String instanceType, URI internalUri, URI externalUri)
    {
        return new MockRemoteAgent(agentId);
    }
}
