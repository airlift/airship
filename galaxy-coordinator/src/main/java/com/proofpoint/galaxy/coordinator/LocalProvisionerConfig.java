package com.proofpoint.galaxy.coordinator;

import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.proofpoint.configuration.Config;

import java.util.List;

public class LocalProvisionerConfig
{
    private List<String> localAgentUri = ImmutableList.of();

    public List<String> getLocalAgentUris()
    {
        return localAgentUri;
    }

    @Config("coordinator.agent-uri")
    public LocalProvisionerConfig setLocalAgentUris(String localAgentUris)
    {
        this.localAgentUri = ImmutableList.copyOf(Splitter.on(',').omitEmptyStrings().trimResults().split(localAgentUris));
        return this;
    }
}
