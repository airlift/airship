package com.proofpoint.galaxy.shared;

import com.google.common.base.Preconditions;

import javax.annotation.concurrent.Immutable;
import java.net.URI;
import java.util.List;
import java.util.UUID;

@Immutable
public class AgentStatus
{
    private final URI uri;
    private final UUID agentId;
    private final List<SlotStatus> slots;

    public AgentStatus(URI uri, UUID agentId, List<SlotStatus> slots)
    {
        Preconditions.checkNotNull(uri, "uri is null");
        Preconditions.checkNotNull(agentId, "agentId is null");
        Preconditions.checkNotNull(slots, "slots is null");
        this.uri = uri;
        this.agentId = agentId;
        this.slots = slots;
    }

    public URI getUri()
    {
        return uri;
    }

    public UUID getAgentId()
    {
        return agentId;
    }

    public List<SlotStatus> getSlots()
    {
        return slots;
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

        AgentStatus that = (AgentStatus) o;

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
        sb.append("AgentStatus");
        sb.append("{uri=").append(uri);
        sb.append(", agentId=").append(agentId);
        sb.append(", slots=").append(slots);
        sb.append('}');
        return sb.toString();
    }
}
