package com.proofpoint.galaxy.coordinator;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.MapEvictionListener;
import com.google.common.collect.MapMaker;
import com.google.inject.Inject;
import com.proofpoint.galaxy.shared.AgentStatus;
import com.proofpoint.galaxy.shared.SlotStatus;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class Coordinator
{
    private final RemoteSlotFactory remoteSlotFactory;
    private final ConcurrentMap<UUID, AgentStatus> agents;

    // write access to agents and slots must be protected by a
    // lock since they are two independent view of the same data
    private final ReadWriteLock slotsLock = new ReentrantReadWriteLock();
    private final Map<UUID, RemoteSlot> slots;

    @Inject
    public Coordinator(RemoteSlotFactory remoteSlotFactory, CoordinatorConfig config)
    {
        Preconditions.checkNotNull(remoteSlotFactory, "remoteSlotFactory is null");

        this.remoteSlotFactory = remoteSlotFactory;
        agents = new MapMaker()
                .expiration((long) config.getStatusExpiration().toMillis(), TimeUnit.MILLISECONDS)
                .evictionListener(new MapEvictionListener<UUID, AgentStatus>()
                {
                    @Override
                    public void onEviction(UUID id, AgentStatus agentStatus)
                    {
                        try {
                            slotsLock.writeLock().lock();
                            for (SlotStatus slotStatus : agentStatus.getSlots()) {
                                slots.remove(slotStatus.getId());
                            }
                        }
                        finally {
                            slotsLock.writeLock().unlock();
                        }

                    }
                })
                .makeMap();

        slots = new MapMaker().makeMap();
    }

    public List<AgentStatus> getAllAgentStatus()
    {
        return ImmutableList.copyOf(agents.values());
    }

    public AgentStatus getAgentStatus(UUID agentId)
    {
        return agents.get(agentId);
    }

    public void updateAgentStatus(AgentStatus status)
    {
        slotsLock.writeLock().lock();
        try {
            agents.put(status.getAgentId(), status);
            for (SlotStatus slotStatus : status.getSlots()) {
                RemoteSlot remoteSlot = slots.get(slotStatus.getId());
                if (remoteSlot != null) {
                    remoteSlot.updateStatus(slotStatus);
                }
                else {
                    slots.put(slotStatus.getId(), remoteSlotFactory.createRemoteSlot(slotStatus));
                }
            }
        }
        finally {
            slotsLock.writeLock().unlock();
        }

    }

    public boolean removeAgentStatus(UUID agentId)
    {
        slotsLock.writeLock().lock();
        try {
            AgentStatus removedAgent = agents.remove(agentId);
            if (removedAgent == null) {
                return false;
            }

            for (SlotStatus slotStatus : removedAgent.getSlots()) {
                slots.remove(slotStatus.getId());
            }
            return true;
        }
        finally {
            slotsLock.writeLock().unlock();
        }

    }

    public RemoteSlot getSlot(UUID slotId)
    {
        return slots.get(slotId);
    }

    public List<RemoteSlot> getAllSlots()
    {
        return ImmutableList.copyOf(slots.values());
    }
}
