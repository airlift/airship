package io.airlift.airship.coordinator;

import io.airlift.airship.shared.AgentLifecycleState;

public interface RemoteAgentFactory
{
    RemoteAgent createRemoteAgent(Instance instance, AgentLifecycleState state);
}
