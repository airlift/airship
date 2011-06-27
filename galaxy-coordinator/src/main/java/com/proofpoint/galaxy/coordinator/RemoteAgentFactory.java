package com.proofpoint.galaxy.coordinator;

import java.util.UUID;

public interface RemoteAgentFactory
{
    RemoteAgent createRemoteAgent(UUID agentId);

}
