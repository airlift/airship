package com.proofpoint.galaxy.coordinator;

import com.proofpoint.configuration.Config;

public class LocalProvisionerConfig
{
    private String localAgentUri;

    public String getLocalAgentUri()
    {
        return localAgentUri;
    }

    @Config("coordinator.agent-uri")
    public void setLocalAgentUri(String localAgentUri)
    {
        this.localAgentUri = localAgentUri;
    }
}
