package com.proofpoint.galaxy;

import com.google.common.base.Preconditions;

import javax.annotation.concurrent.Immutable;
import java.util.List;
import java.util.UUID;

@Immutable
public class AgentStatus
{
    private final UUID agentId;
    private final List<SlotStatus> slots;

    public AgentStatus(UUID agentId, List<SlotStatus> slots)
    {
        Preconditions.checkNotNull(agentId, "agentId is null");
        Preconditions.checkNotNull(slots, "slots is null");
        this.agentId = agentId;
        this.slots = slots;
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
        final StringBuffer sb = new StringBuffer();
        sb.append("AgentStatus");
        sb.append("{agentId=").append(agentId);
        sb.append(", slots=").append(slots);
        sb.append('}');
        return sb.toString();
    }
}
