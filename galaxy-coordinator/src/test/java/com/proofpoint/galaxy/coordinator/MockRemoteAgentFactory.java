package com.proofpoint.galaxy.coordinator;

import com.google.common.base.Ticker;

import java.util.UUID;

public class MockRemoteAgentFactory implements RemoteAgentFactory
{
    private final Ticker ticker;

    public MockRemoteAgentFactory()
    {
        this(Ticker.systemTicker());
    }

    public MockRemoteAgentFactory(Ticker ticker)
    {
        this.ticker = ticker;
    }

    @Override
    public RemoteAgent createRemoteAgent(String agentId)
    {
        return new MockRemoteAgent(agentId, ticker);
    }
}
