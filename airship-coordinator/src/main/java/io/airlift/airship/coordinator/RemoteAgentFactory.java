package com.proofpoint.galaxy.coordinator;

import com.proofpoint.galaxy.shared.AgentLifecycleState;

public interface RemoteAgentFactory
{
    RemoteAgent createRemoteAgent(Instance instance, AgentLifecycleState state);
}
