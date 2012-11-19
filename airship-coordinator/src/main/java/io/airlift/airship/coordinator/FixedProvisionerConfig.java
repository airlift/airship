package io.airlift.airship.coordinator;

import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import io.airlift.configuration.Config;

import java.util.List;

public class FixedProvisionerConfig
{
    private List<String> localAgentUris = ImmutableList.of();

    public List<String> getLocalAgentUris()
    {
        return localAgentUris;
    }

    @Config("coordinator.agent-uri")
    public FixedProvisionerConfig setLocalAgentUris(String localAgentUris)
    {
        this.localAgentUris = ImmutableList.copyOf(Splitter.on(',').omitEmptyStrings().trimResults().split(localAgentUris));
        return this;
    }

    public FixedProvisionerConfig setLocalAgentUris(List<String> localAgentUri)
    {
        this.localAgentUris = ImmutableList.copyOf(localAgentUri);
        return this;
    }
}
