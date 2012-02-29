package com.proofpoint.galaxy.coordinator;

import com.proofpoint.galaxy.shared.AgentStatus;

public interface RemoteAgentFactory
{
    RemoteAgent createRemoteAgent(AgentStatus agentStatus);
}
