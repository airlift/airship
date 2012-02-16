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
import java.util.UUID;

@Immutable
public class AgentStatus
{
    private final String agentId;
    private final AgentLifecycleState state;
    private final URI internalUri;
    private final URI externalUri;
    private final Map<UUID, SlotStatus> slots;
    private final String location;
    private final String instanceType;
    private final Map<String, Integer> resources;
    private final String version;

    public AgentStatus(String agentId,
            AgentLifecycleState state,
            URI internalUri,
            URI externalUri,
            String location,
            String instanceType,
            List<SlotStatus> slots,
            Map<String, Integer> resources)
    {
        Preconditions.checkNotNull(agentId, "agentId is null");
        Preconditions.checkNotNull(slots, "slots is null");
        Preconditions.checkNotNull(resources, "resources is null");

        this.internalUri = internalUri;
        this.externalUri = externalUri;
        this.state = state;
        this.agentId = agentId;
        this.location = location;
        this.instanceType = instanceType;
        this.slots = Maps.uniqueIndex(slots, SlotStatus.uuidGetter());
        this.resources = ImmutableMap.copyOf(resources);
        this.version = VersionsUtil.createAgentVersion(agentId, state, slots, resources);
    }

    public AgentStatus(AgentStatus agentStatus, AgentLifecycleState state)
    {
        Preconditions.checkNotNull(agentStatus, "agentStatus is null");
        Preconditions.checkNotNull(state, "state is null");

        this.internalUri = agentStatus.internalUri;
        this.externalUri = agentStatus.externalUri;
        this.state = state;
        this.agentId = agentStatus.agentId;
        this.location = agentStatus.location;
        this.instanceType = agentStatus.instanceType;
        this.slots = agentStatus.slots;
        this.resources = agentStatus.resources;
        this.version = VersionsUtil.createAgentVersion(agentId, state, slots.values(), resources);
    }

    public String getAgentId()
    {
        return agentId;
    }

    public AgentLifecycleState getState()
    {
        return state;
    }
    
    public AgentStatus updateState(AgentLifecycleState state)
    {
        return new AgentStatus(this, state);
    }

    public URI getInternalUri()
    {
        return internalUri;
    }

    public URI getExternalUri()
    {
        return externalUri;
    }

    public String getLocation()
    {
        return location;
    }

    public String getInstanceType()
    {
        return instanceType;
    }

    public SlotStatus getSlotStatus(UUID slotId)
    {
        return slots.get(slotId);
    }

    public List<SlotStatus> getSlotStatuses()
    {
        return ImmutableList.copyOf(slots.values());
    }

    public Map<String, Integer> getResources()
    {
        return resources;
    }

    public String getVersion()
    {
        return version;
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
        sb.append(", internalUri=").append(internalUri);
        sb.append(", externalUri=").append(externalUri);
        sb.append(", slots=").append(slots.values());
        sb.append(", resources=").append(resources);
        sb.append(", version=").append(version);
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
