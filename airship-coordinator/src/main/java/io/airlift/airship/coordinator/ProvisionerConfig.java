package io.airlift.airship.coordinator;

import io.airlift.configuration.Config;
import io.airlift.configuration.ConfigDescription;

import javax.validation.constraints.NotNull;

@SuppressWarnings("unchecked")
public abstract class ProvisionerConfig<T extends ProvisionerConfig<T>>
{
    private String airshipVersion;
    private String agentDefaultConfig;

    @NotNull
    public String getAirshipVersion()
    {
        return airshipVersion;
    }

    @Config("airship.version")
    public T setAirshipVersion(String airshipVersion)
    {
        this.airshipVersion = airshipVersion;
        return (T) this;
    }

    @NotNull
    public String getAgentDefaultConfig()
    {
        return agentDefaultConfig;
    }

    @Config("coordinator.agent.default-config")
    @ConfigDescription("Default config for provisioned agents")
    public T setAgentDefaultConfig(String agentDefaultConfig)
    {
        this.agentDefaultConfig = agentDefaultConfig;
        return (T) this;
    }
}
