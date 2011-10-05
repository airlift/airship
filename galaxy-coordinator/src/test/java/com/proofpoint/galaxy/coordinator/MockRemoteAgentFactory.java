package com.proofpoint.galaxy.coordinator;

import com.google.common.base.Ticker;

import java.net.URI;

public class MockRemoteAgentFactory implements RemoteAgentFactory
{
    @Override
    public RemoteAgent createRemoteAgent(String agentId, URI uri)
    {
        return new MockRemoteAgent(agentId, uri);
    }
}
