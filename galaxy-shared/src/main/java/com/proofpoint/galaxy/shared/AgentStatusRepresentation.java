package com.proofpoint.galaxy.shared;

import com.google.common.base.Function;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableList.Builder;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import org.codehaus.jackson.annotate.JsonAutoDetect;
import org.codehaus.jackson.annotate.JsonCreator;
import org.codehaus.jackson.annotate.JsonMethod;
import org.codehaus.jackson.annotate.JsonProperty;

import javax.validation.constraints.NotNull;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Map;

@JsonAutoDetect(JsonMethod.NONE)
public class AgentStatusRepresentation
{
    public static List<AgentStatusRepresentation> toAgentStatusRepresentations(Iterable<AgentStatus> agents)
    {
        return ImmutableList.copyOf(Iterables.transform(agents, new Function<AgentStatus, AgentStatusRepresentation>()
        {
            @Override
            public AgentStatusRepresentation apply(AgentStatus agent)
            {
                return from(agent);
            }
        }));
    }

    private final String agentId;
    private final List<SlotStatusRepresentation> slots;
    private final URI self;
    private final URI externalUri;
    private final AgentLifecycleState state;
    private final String location;
    private final String instanceType;
    private final Map<String, Integer> resources;
    private final String version;

    public static Function<AgentStatus, AgentStatusRepresentation> fromAgentStatus()
    {
        return new Function<AgentStatus, AgentStatusRepresentation>()
        {
            public AgentStatusRepresentation apply(AgentStatus status)
            {
                return from(status);
            }
        };
    }

    public static AgentStatusRepresentation from(AgentStatus status) {
        Builder<SlotStatusRepresentation> builder = ImmutableList.builder();
        for (SlotStatus slot : status.getSlotStatuses()) {
            builder.add(SlotStatusRepresentation.from(slot));
        }
        return new AgentStatusRepresentation(
                status.getAgentId(),
                status.getState(),
                status.getInternalUri(),
                status.getExternalUri(),
                status.getLocation(),
                status.getInstanceType(),
                builder.build(),
                status.getResources(),
                status.getVersion());
    }

    @JsonCreator
    public AgentStatusRepresentation(
            @JsonProperty("agentId") String agentId,
            @JsonProperty("state") AgentLifecycleState state,
            @JsonProperty("self") URI self,
            @JsonProperty("externalUri") URI externalUri,
            @JsonProperty("location") String location,
            @JsonProperty("instanceType") String instanceType,
            @JsonProperty("slots") List<SlotStatusRepresentation> slots,
            @JsonProperty("resources") Map<String, Integer> resources,
            @JsonProperty("version") String version)
    {
        this.agentId = agentId;
        this.slots = slots;
        this.self = self;
        this.externalUri = externalUri;
        this.state = state;
        this.location = location;
        this.instanceType = instanceType;
        if (resources != null) {
            this.resources = ImmutableMap.copyOf(resources);
        }
        else {
            this.resources = ImmutableMap.of();
        }
        this.version = version;
    }

    @JsonProperty
    @NotNull
    public String getAgentId()
    {
        return agentId;
    }

    @JsonProperty
    @NotNull
    public List<SlotStatusRepresentation> getSlots()
    {
        return slots;
    }

    @JsonProperty
    @NotNull
    public URI getSelf()
    {
        return self;
    }

    @JsonProperty
    @NotNull
    public URI getExternalUri()
    {
        return externalUri;
    }

    @JsonProperty
    public AgentLifecycleState getState()
    {
        return state;
    }

    @JsonProperty
    public String getLocation()
    {
        return location;
    }

    @JsonProperty
    public String getInstanceType()
    {
        return instanceType;
    }

    @JsonProperty
    public Map<String, Integer> getResources()
    {
        return resources;
    }

    @JsonProperty
    public String getVersion()
    {
        return version;
    }

    public String getInternalHost() {
        if (self == null) {
            return null;
        }
        return self.getHost();
    }

    public String getInternalIp()
    {
        String host = getInternalHost();
        if (host == null) {
            return null;
        }

        if ("localhost".equalsIgnoreCase(host)) {
            return "127.0.0.1";
        }

        try {
            return InetAddress.getByName(host).getHostAddress();
        }
        catch (UnknownHostException e) {
            return "unknown";
        }
    }

    public String getExternalHost() {
        if (externalUri == null) {
            return null;
        }
        return externalUri.getHost();
    }

    public AgentStatus toAgentStatus()
    {
        Builder<SlotStatus> builder = ImmutableList.builder();
        for (SlotStatusRepresentation slot : slots) {
            builder.add(slot.toSlotStatus());
        }
        return new AgentStatus(agentId, AgentLifecycleState.ONLINE, self, externalUri, location, instanceType, builder.build(), resources);
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        AgentStatusRepresentation that = (AgentStatusRepresentation) o;

        if (!agentId.equals(that.agentId)) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode()
    {
        return agentId.hashCode();
    }

    @Override
    public String toString()
    {
        final StringBuilder sb = new StringBuilder();
        sb.append("AgentStatusRepresentation");
        sb.append("{agentId='").append(agentId).append('\'');
        sb.append(", slots=").append(slots);
        sb.append(", self=").append(self);
        sb.append(", externalUri=").append(externalUri);
        sb.append(", state=").append(state);
        sb.append(", location='").append(location).append('\'');
        sb.append(", instanceType='").append(instanceType).append('\'');
        sb.append(", resources=").append(resources);
        sb.append(", version='").append(version).append('\'');
        sb.append('}');
        return sb.toString();
    }
}
