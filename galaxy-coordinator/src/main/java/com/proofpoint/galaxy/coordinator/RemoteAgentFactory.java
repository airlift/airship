package com.proofpoint.galaxy.coordinator;

import java.net.URI;

public interface RemoteAgentFactory
{
    RemoteAgent createRemoteAgent(String agentId, URI uri);

}
