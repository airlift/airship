package io.airlift.airship.coordinator;

import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import io.airlift.configuration.Config;
import io.airlift.configuration.ConfigDescription;

import java.util.List;

public class FixedProvisionerConfig
{
    private List<String> localAgentUris = ImmutableList.of();

    @Deprecated
    public List<String> getLocalAgentUris()
    {
        return localAgentUris;
    }

    @Deprecated
    @Config("coordinator.agent-uri")
    @ConfigDescription("DEPRECATED: Fixed provisioner has been replaced with static provisioner.")
    public FixedProvisionerConfig setLocalAgentUris(String localAgentUris)
    {
        this.localAgentUris = ImmutableList.copyOf(Splitter.on(',').omitEmptyStrings().trimResults().split(localAgentUris));
        return this;
    }

    @Deprecated
    public FixedProvisionerConfig setLocalAgentUris(List<String> localAgentUri)
    {
        this.localAgentUris = ImmutableList.copyOf(localAgentUri);
        return this;
    }
}
