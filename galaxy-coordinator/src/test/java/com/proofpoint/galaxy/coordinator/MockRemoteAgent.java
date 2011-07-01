package com.proofpoint.galaxy.coordinator;

import com.google.common.base.Function;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.proofpoint.galaxy.shared.AgentLifecycleState;
import com.proofpoint.galaxy.shared.AgentStatus;
import com.proofpoint.galaxy.shared.Installation;
import com.proofpoint.galaxy.shared.SlotLifecycleState;
import com.proofpoint.galaxy.shared.SlotStatus;

import java.net.URI;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import static com.google.common.collect.Sets.newHashSet;
import static com.proofpoint.galaxy.shared.AgentLifecycleState.*;
import static com.proofpoint.galaxy.shared.SlotLifecycleState.TERMINATED;

public class MockRemoteAgent implements RemoteAgent
{
    private final ConcurrentMap<UUID, MockRemoteSlot> slots = new ConcurrentHashMap<UUID, MockRemoteSlot>();
    private final UUID agentId;
    private AgentLifecycleState state;
    private URI uri;

    public MockRemoteAgent(UUID agentId)
    {
        Preconditions.checkNotNull(agentId, "agentId is null");
        this.agentId = agentId;
        this.uri = URI.create("fake://agent/" + agentId);
        state = ONLINE;
    }

    @Override
    public AgentStatus status()
    {
        return new AgentStatus(agentId, state, uri, ImmutableList.copyOf(Iterables.transform(slots.values(), new Function<MockRemoteSlot, SlotStatus>()
        {
            public SlotStatus apply(MockRemoteSlot slot)
            {
                return slot.status();
            }
        })));
    }

    @Override
    public List<? extends RemoteSlot> getSlots()
    {
        return ImmutableList.copyOf(slots.values());
    }

    @Override
    public void updateStatus(AgentStatus status)
    {
        Set<UUID> updatedSlots = newHashSet();
        for (SlotStatus slotStatus : status.getSlotStatuses()) {
            MockRemoteSlot remoteSlot = slots.get(slotStatus.getId());
            if (remoteSlot != null) {
                remoteSlot.updateStatus(slotStatus);
            }
            else {
                slots.put(slotStatus.getId(), new MockRemoteSlot(slotStatus));
            }
            updatedSlots.add(slotStatus.getId());
        }

        // remove all slots that were not updated
        slots.keySet().retainAll(updatedSlots);

        uri = status.getUri();
    }

    @Override
    public void agentOffline()
    {
        uri = null;
        state = OFFLINE;
        for (MockRemoteSlot remoteSlot : slots.values()) {
            remoteSlot.updateStatus(new SlotStatus(remoteSlot.status(), SlotLifecycleState.UNKNOWN));
        }
    }

    @Override
    public SlotStatus install(Installation installation)
    {
        Preconditions.checkNotNull(installation, "installation is null");
        Preconditions.checkState(state != OFFLINE, "agent is offline");

        UUID slotId = UUID.randomUUID();
        SlotStatus slotStatus = new SlotStatus(slotId, "", uri.resolve("slot/" + slotId), SlotLifecycleState.STOPPED, installation.getAssignment());
        MockRemoteSlot slot = new MockRemoteSlot(slotStatus);
        slots.put(slotId, slot);

        return slotStatus;
    }

    @Override
    public SlotStatus terminateSlot(UUID slotId)
    {
        Preconditions.checkNotNull(slotId, "slotId is null");

        MockRemoteSlot slot = slots.get(slotId);
        SlotStatus status = slot.terminate();
        if (status.getState() == TERMINATED) {
            slots.remove(slotId);
        }
        return status;
    }
}