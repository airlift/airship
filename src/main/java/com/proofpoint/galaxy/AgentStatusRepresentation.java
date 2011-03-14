package com.proofpoint.galaxy;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableList.Builder;
import org.codehaus.jackson.annotate.JsonCreator;
import org.codehaus.jackson.annotate.JsonProperty;

import javax.validation.constraints.NotNull;
import javax.ws.rs.core.UriInfo;
import java.net.URI;
import java.util.List;
import java.util.UUID;

public class AgentStatusRepresentation
{
    private final String agentId;
    private final List<SlotStatusRepresentation> slots;
    private final URI self;

    public static AgentStatusRepresentation from(AgentStatus status, UriInfo uriInfo) {
        Builder<SlotStatusRepresentation> builder = ImmutableList.builder();
        for (SlotStatus slot : status.getSlots()) {
            builder.add(SlotStatusRepresentation.from(slot, uriInfo));
        }
        return new AgentStatusRepresentation(status.getAgentId().toString(), builder.build(), getSelfUri(uriInfo, status));
    }

    public static URI getSelfUri(UriInfo uriInfo, AgentStatus status)
    {
        return uriInfo.getBaseUriBuilder().path("/v1/agent/" + status.getAgentId()).build();
    }


    @JsonCreator
    public AgentStatusRepresentation(@JsonProperty("agentId") String agentId, @JsonProperty("slots") List<SlotStatusRepresentation> slots, @JsonProperty("slots") URI self)
    {
        this.agentId = agentId;
        this.slots = slots;
        this.self = self;
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

    public AgentStatus toAgentStatus()
    {
        Builder<SlotStatus> builder = ImmutableList.builder();
        for (SlotStatusRepresentation slot : slots) {
            builder.add(slot.toSlotStatus());
        }
        return new AgentStatus(UUID.fromString(agentId), builder.build());
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
        final StringBuffer sb = new StringBuffer();
        sb.append("AgentStatusRepresentation");
        sb.append("{agentId=").append(agentId);
        sb.append(", slots=").append(slots);
        sb.append('}');
        return sb.toString();
    }
}
