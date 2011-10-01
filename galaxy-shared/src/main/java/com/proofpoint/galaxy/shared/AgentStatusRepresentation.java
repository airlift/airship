package com.proofpoint.galaxy.shared;

import com.google.common.base.Function;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableList.Builder;
import org.codehaus.jackson.annotate.JsonAutoDetect;
import org.codehaus.jackson.annotate.JsonCreator;
import org.codehaus.jackson.annotate.JsonMethod;
import org.codehaus.jackson.annotate.JsonProperty;

import javax.validation.constraints.NotNull;
import java.net.URI;
import java.util.List;
import java.util.UUID;

@JsonAutoDetect(JsonMethod.NONE)
public class AgentStatusRepresentation
{
    private final String agentId;
    private final String shortId;
    private final List<SlotStatusRepresentation> slots;
    private final URI self;
    private final AgentLifecycleState state;
    private final String location;
    private final String instanceType;

    private final static int MAX_ID_SIZE = UUID.randomUUID().toString().length();

    public static Function<AgentStatus, AgentStatusRepresentation> fromAgentStatusWithShortIdPrefixSize(final int size)
    {
        return new Function<AgentStatus, AgentStatusRepresentation>()
        {
            public AgentStatusRepresentation apply(AgentStatus status)
            {
                return from(status, size);
            }
        };
    }

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
        return from(status, MAX_ID_SIZE) ;
    }

    public static AgentStatusRepresentation from(AgentStatus status, int shortIdPrefixSize) {
        Builder<SlotStatusRepresentation> builder = ImmutableList.builder();
        for (SlotStatus slot : status.getSlotStatuses()) {
            builder.add(SlotStatusRepresentation.from(slot));
        }
        return new AgentStatusRepresentation(
                status.getAgentId(),
                status.getAgentId().toString().substring(0, shortIdPrefixSize),
                status.getState(),
                status.getUri(),
                status.getLocation(),
                status.getInstanceType(),
                builder.build());
    }

    @JsonCreator
    public AgentStatusRepresentation(
            @JsonProperty("agentId") String agentId,
            @JsonProperty("shortId") String shortId,
            @JsonProperty("state") AgentLifecycleState state,
            @JsonProperty("self") URI self,
            @JsonProperty("location") String location,
            @JsonProperty("instanceType") String instanceType, @JsonProperty("slots") List<SlotStatusRepresentation> slots)
    {
        this.agentId = agentId;
        this.shortId = shortId;
        this.slots = slots;
        this.self = self;
        this.state = state;
        this.location = location;
        this.instanceType = instanceType;
    }

    @JsonProperty
    @NotNull
    public String getAgentId()
    {
        return agentId;
    }

    @JsonProperty
    public String getShortId()
    {
        return shortId;
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

    public AgentStatus toAgentStatus()
    {
        Builder<SlotStatus> builder = ImmutableList.builder();
        for (SlotStatusRepresentation slot : slots) {
            builder.add(slot.toSlotStatus());
        }
        return new AgentStatus(agentId, AgentLifecycleState.ONLINE, self, location, instanceType, builder.build());
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
        sb.append("{agentId=").append(agentId);
        sb.append(", shortId=").append(shortId);
        sb.append(", state=").append(state);
        sb.append(", location=").append(location);
        sb.append(", instanceType=").append(instanceType);
        sb.append(", slots=").append(slots);
        sb.append('}');
        return sb.toString();
    }
}
