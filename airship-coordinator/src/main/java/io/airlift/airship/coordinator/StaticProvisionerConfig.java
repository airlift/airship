package io.airlift.airship.coordinator;

import io.airlift.configuration.Config;

import javax.validation.constraints.NotNull;
import java.net.URI;

public class StaticProvisionerConfig
{
    private URI coordinatorsUri = URI.create("file:etc/coordinators.txt");
    private URI agentsUri = URI.create("file:etc/agents.txt");

    @NotNull
    public URI getCoordinatorsUri()
    {
        return coordinatorsUri;
    }

    @Config("coordinator.coordinators-uri")
    public StaticProvisionerConfig setCoordinatorsUri(URI coordinatorsUri)
    {
        this.coordinatorsUri = coordinatorsUri;
        return this;
    }

    @NotNull
    public URI getAgentsUri()
    {
        return agentsUri;
    }

    @Config("coordinator.agents-uri")
    public StaticProvisionerConfig setAgentsUri(URI agentsUri)
    {
        this.agentsUri = agentsUri;
        return this;
    }
}
