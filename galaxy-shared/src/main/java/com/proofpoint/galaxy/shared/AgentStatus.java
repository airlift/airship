package com.proofpoint.galaxy.shared;

import com.google.common.base.Function;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;

import javax.annotation.concurrent.Immutable;
import java.net.URI;
import java.util.List;
import java.util.Map;

@Immutable
public class AgentStatus
{
    private final String agentId;
    private final AgentLifecycleState state;
    private final URI uri;
    private final Map<String, SlotStatus> slots;
    private final String location;
    private final String instanceType;
    private final Map<String, Integer> resources;

    public AgentStatus(String agentId, AgentLifecycleState state, URI uri, String location, String instanceType, List<SlotStatus> slots, Map<String, Integer> resources)
    {
        Preconditions.checkNotNull(agentId, "agentId is null");
        Preconditions.checkNotNull(slots, "slots is null");
        Preconditions.checkNotNull(resources, "resources is null");

        this.uri = uri;
        this.state = state;
        this.agentId = agentId;
        this.location = location;
        this.instanceType = instanceType;
        this.slots = Maps.uniqueIndex(slots, new Function<SlotStatus, String>()
        {
            public String apply(SlotStatus slotStatus)
            {
                return slotStatus.getName();
            }
        });
        this.resources = ImmutableMap.copyOf(resources);
    }

    public String getAgentId()
    {
        return agentId;
    }

    public AgentLifecycleState getState()
    {
        return state;
    }

    public URI getUri()
    {
        return uri;
    }

    public String getLocation()
    {
        return location;
    }

    public String getInstanceType()
    {
        return instanceType;
    }

    public SlotStatus getSlotStatus(String slotName)
    {
        return slots.get(slotName);
    }

    public List<SlotStatus> getSlotStatuses()
    {
        return ImmutableList.copyOf(slots.values());
    }

    public Map<String, Integer> getResources()
    {
        return resources;
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
        sb.append("{agentId=").append(agentId);
        sb.append(", state=").append(state);
        sb.append(", uri=").append(uri);
        sb.append(", slots=").append(slots);
        sb.append(", resources=").append(resources);
        sb.append('}');
        return sb.toString();
    }


    public static Function<AgentStatus, String> idGetter()
    {
        return new Function<AgentStatus, String>()
        {
            public String apply(AgentStatus input)
            {
                return input.getAgentId();
            }
        };
    }

}
