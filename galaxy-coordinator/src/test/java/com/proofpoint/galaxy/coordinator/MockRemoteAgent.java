package com.proofpoint.galaxy.coordinator;

import com.google.common.base.Function;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.proofpoint.discovery.client.ServiceDescriptor;
import com.proofpoint.galaxy.shared.AgentLifecycleState;
import com.proofpoint.galaxy.shared.AgentStatus;
import com.proofpoint.galaxy.shared.Installation;
import com.proofpoint.galaxy.shared.SlotLifecycleState;
import com.proofpoint.galaxy.shared.SlotStatus;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.collect.Sets.newHashSet;
import static com.proofpoint.galaxy.shared.AgentLifecycleState.*;

public class MockRemoteAgent implements RemoteAgent
{
    private final ConcurrentMap<UUID, SlotStatus> slots = new ConcurrentHashMap<UUID, SlotStatus>();
    private final String agentId;
    private AgentLifecycleState state;
    private URI internalUri;
    private URI externalUri;
    private Map<String,Integer> resources = ImmutableMap.of();

    public MockRemoteAgent(String agentId)
    {
        this.agentId = checkNotNull(agentId, "agentId is null");
        this.internalUri = URI.create("internal://agent/" + agentId);
        this.externalUri = URI.create("external://agent/" + agentId);
        state = ONLINE;
    }

    @Override
    public AgentStatus status()
    {
        return new AgentStatus(agentId, state, internalUri, externalUri, "unknown/location", "instance.type", ImmutableList.copyOf(slots.values()), resources);
    }

    @Override
    public URI getInternalUri()
    {
        return internalUri;
    }

    @Override
    public void setInternalUri(URI uri)
    {
        this.internalUri = uri;
    }

    @Override
    public URI getExternalUri()
    {
        return externalUri;
    }

    @Override
    public void setExternalUri(URI externalUri)
    {
        this.externalUri = externalUri;
    }

    @Override
    public List<? extends RemoteSlot> getSlots()
    {
        return ImmutableList.copyOf(Iterables.transform(slots.values(), new Function<SlotStatus, MockRemoteSlot>()
        {
            @Override
            public MockRemoteSlot apply(SlotStatus slotStatus)
            {
                return new MockRemoteSlot(slotStatus, MockRemoteAgent.this);
            }
        }));
    }

    @Override
    public void updateStatus()
    {
    }

    @Override
    public void setStatus(AgentStatus status)
    {
        Set<UUID> updatedSlots = newHashSet();
        for (SlotStatus slotStatus : status.getSlotStatuses()) {
            updatedSlots.add(slotStatus.getId());
            slots.put(slotStatus.getId(), slotStatus);
        }

        // remove all slots that were not updated
        slots.keySet().retainAll(updatedSlots);

        state = status.getState();
        internalUri = status.getInternalUri();
        if (status.getResources() != null) {
            resources = ImmutableMap.copyOf(status.getResources());
        }
        else {
            resources = ImmutableMap.of();
        }
    }

    public void setSlotStatus(SlotStatus slotStatus)
    {
        slots.put(slotStatus.getId(), slotStatus);
    }

    @Override
    public void setServiceInventory(List<ServiceDescriptor> serviceInventory)
    {
    }

    @Override
    public SlotStatus install(Installation installation)
    {
        Preconditions.checkNotNull(installation, "installation is null");
        Preconditions.checkState(state != OFFLINE, "agent is offline");

        UUID slotId = UUID.randomUUID();
        SlotStatus slotStatus = new SlotStatus(slotId,
                "",
                internalUri.resolve("slot/" + slotId),
                externalUri.resolve("slot/" + slotId),
                "location",
                SlotLifecycleState.STOPPED,
                installation.getAssignment(),
                "/" + slotId,
                installation.getResources());
        slots.put(slotId, slotStatus);

        return slotStatus;
    }
}
