package io.airlift.airship.shared;

import com.google.common.base.Function;
import com.google.common.base.Objects;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.google.common.collect.Iterables.transform;
import static com.google.common.collect.Maps.newHashMap;
import static io.airlift.airship.shared.SlotLifecycleState.TERMINATED;

@Immutable
public class AgentStatus
{
    private final String agentId;
    private final AgentLifecycleState state;
    private final String instanceId;
    private final URI internalUri;
    private final URI externalUri;
    private final Map<UUID, SlotStatus> slots;
    private final String location;
    private final String instanceType;
    private final Map<String, Integer> resources;
    private final String version;

    public AgentStatus(String agentId,
            AgentLifecycleState state,
            final String instanceId,
            URI internalUri,
            URI externalUri,
            String location,
            String instanceType,
            Iterable<SlotStatus> slots,
            Map<String, Integer> resources)
    {
        Preconditions.checkNotNull(slots, "slots is null");
        Preconditions.checkNotNull(resources, "resources is null");

        this.agentId = agentId;
        this.state = state;
        this.instanceId = instanceId;
        this.internalUri = internalUri;
        this.externalUri = externalUri;
        this.location = location;
        this.instanceType = instanceType;

        slots = transform(slots, new Function<SlotStatus, SlotStatus>()
        {
            public SlotStatus apply(@Nullable SlotStatus slotStatus)
            {
                if (!Objects.equal(slotStatus.getInstanceId(), instanceId)) {
                    slotStatus = slotStatus.changeInstanceId(instanceId);
                }
                return slotStatus;
            }
        });
        this.slots = Maps.uniqueIndex(slots, SlotStatus.uuidGetter());

        this.resources = ImmutableMap.copyOf(resources);
        this.version = VersionsUtil.createAgentVersion(agentId, state, slots, resources);
    }

    public String getAgentId()
    {
        return agentId;
    }

    public AgentLifecycleState getState()
    {
        return state;
    }

    public String getInstanceId()
    {
        return instanceId;
    }

    public AgentStatus changeState(AgentLifecycleState state)
    {
        return new AgentStatus(agentId, state, instanceId, internalUri, externalUri, location, instanceType, slots.values(), resources);
    }

    public AgentStatus changeSlotStatus(SlotStatus slotStatus)
    {
        Map<UUID,SlotStatus> slots = newHashMap(this.slots);
        if (slotStatus.getState() != TERMINATED) {
            slots.put(slotStatus.getId(), slotStatus);
        } else {
            slots.remove(slotStatus.getId());
        }
        return new AgentStatus(agentId, state, instanceId, internalUri, externalUri, location, instanceType, slots.values(), resources);
    }

    public AgentStatus changeAllSlotsState(SlotLifecycleState slotState)
    {
        Map<UUID,SlotStatus> slots = newHashMap(this.slots);
        for (SlotStatus slotStatus : slots.values()) {
            // set all slots to unknown state
            slots.put(slotStatus.getId(), slotStatus.changeState(slotState));
        }
        return new AgentStatus(agentId, state, instanceId, internalUri, externalUri, location, instanceType, slots.values(), resources);
    }

    public AgentStatus changeInternalUri(URI internalUri)
    {
        return new AgentStatus(agentId, state, instanceId, internalUri, externalUri, location, instanceType, slots.values(), resources);
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
        sb.append(", instanceId=").append(instanceId);
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

    public static Function<AgentStatus, String> locationGetter()
    {
        return new Function<AgentStatus, String>()
        {
            public String apply(AgentStatus input)
            {
                return input.getLocation();
            }
        };
    }
}
