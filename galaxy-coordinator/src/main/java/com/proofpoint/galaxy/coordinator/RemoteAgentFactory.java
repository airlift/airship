package com.proofpoint.galaxy.coordinator;

public interface RemoteAgentFactory
{
    RemoteAgent createRemoteAgent(String agentId);

}
