package com.proofpoint.galaxy.coordinator;

import com.google.common.base.Function;
import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.MapMaker;
import com.google.inject.Inject;
import com.proofpoint.galaxy.shared.AgentStatus;
import com.proofpoint.galaxy.shared.Installation;
import com.proofpoint.galaxy.shared.SlotLifecycleState;
import com.proofpoint.galaxy.shared.SlotStatus;

import java.util.EnumSet;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentMap;

import static com.google.common.collect.Iterables.concat;
import static com.google.common.collect.Iterables.filter;
import static com.google.common.collect.Iterables.transform;
import static com.proofpoint.galaxy.shared.SlotLifecycleState.RUNNING;
import static com.proofpoint.galaxy.shared.SlotLifecycleState.STOPPED;

public class Coordinator
{
    private final ConcurrentMap<UUID, RemoteAgent> agents;

    @Inject
    public Coordinator(final RemoteAgentFactory remoteAgentFactory)
    {
        Preconditions.checkNotNull(remoteAgentFactory, "remoteAgentFactory is null");

        agents = new MapMaker().makeComputingMap(new Function<UUID, RemoteAgent>()
        {
            public RemoteAgent apply(UUID agentId)
            {
                return remoteAgentFactory.createRemoteAgent(agentId);
            }
        });
    }

    public List<AgentStatus> getAllAgentStatus()
    {
        return ImmutableList.copyOf(Iterables.transform(agents.values(), new Function<RemoteAgent, AgentStatus>()
        {
            public AgentStatus apply(RemoteAgent agent)
            {
                return agent.status();
            }
        }));
    }

    public AgentStatus getAgentStatus(UUID agentId)
    {
        RemoteAgent agent = agents.get(agentId);
        if (agent == null) {
            return null;
        }
        return agent.status();
    }

    public void updateAgentStatus(AgentStatus status)
    {
        agents.get(status.getAgentId()).updateStatus(status);
    }

    public boolean agentOffline(UUID agentId)
    {
        if (!agents.containsKey(agentId)) {
            return false;
        }
        agents.get(agentId).agentOffline();
        return true;
    }

    public boolean removeAgent(UUID agentId)
    {
        return agents.remove(agentId) != null;
    }

    // todo this is only used for testing
    public RemoteSlot getSlot(UUID slotId)
    {
        Preconditions.checkNotNull(slotId, "slotId is null");
        for (RemoteAgent remoteAgent : agents.values()) {
            for (RemoteSlot slot : remoteAgent.getSlots()) {
                if (slotId.equals(slot.getId()))  {
                    return slot;
                }
            }
        }
        return null;
    }

    public List<? extends RemoteSlot> getAllSlots()
    {
        return ImmutableList.copyOf(concat(Iterables.transform(agents.values(), new Function<RemoteAgent, List<? extends RemoteSlot>>()
        {
            public List<? extends RemoteSlot> apply(RemoteAgent agent)
            {
                return agent.getSlots();
            }
        })));
    }

    public List<RemoteAgent> getAgents()
    {
        throw new UnsupportedOperationException();
    }


    public List<SlotStatus> install(final Installation installation, Predicate<RemoteAgent> filter)
    {
        return ImmutableList.copyOf(transform(filter(getAgents(), filter), new Function<RemoteAgent, SlotStatus>()
        {
            public SlotStatus apply(RemoteAgent agent)
            {
                return agent.install(installation);
            }
        }));
    }

    public List<SlotStatus> assign(Predicate<SlotStatus> filter, final Installation installation)
    {
        return ImmutableList.copyOf(transform(filter(getAllSlots(), filterBy(filter)), new Function<RemoteSlot, SlotStatus>()
        {
            @Override
            public SlotStatus apply(RemoteSlot slot)
            {
                return slot.assign(installation);
            }
        }));
    }

    public List<SlotStatus> clear(Predicate<SlotStatus> filter)
    {
        return ImmutableList.copyOf(transform(filter(getAllSlots(), filterBy(filter)), new Function<RemoteSlot, SlotStatus>()
        {
            @Override
            public SlotStatus apply(RemoteSlot slot)
            {
                return slot.clear();
            }
        }));
    }

    public List<SlotStatus> setState(final SlotLifecycleState state, Predicate<SlotStatus> filter)
    {
        Preconditions.checkArgument(EnumSet.of(RUNNING, STOPPED).contains(state), "Unsupported lifecycle state: " + state);

        return ImmutableList.copyOf(transform(filter(getAllSlots(), filterBy(filter)), new Function<RemoteSlot, SlotStatus>() {
            @Override
            public SlotStatus apply(RemoteSlot slot)
            {
                SlotStatus slotStatus = null;
                switch (state) {
                    case RUNNING:
                        slotStatus = slot.start();
                        break;
                    case STOPPED:
                        slotStatus = slot.stop();
                        break;
                }
                return slotStatus;
            }
        }));
    }

    public List<SlotStatus> getAllSlotsStatus(Predicate<SlotStatus> slotFilter)
    {
        return ImmutableList.copyOf(filter(transform(getAllSlots(), getSlotStatus()), slotFilter));
    }

    private Predicate<RemoteSlot> filterBy(final Predicate<SlotStatus> filter)
    {
        return new Predicate<RemoteSlot>()
        {
            @Override
            public boolean apply(RemoteSlot input)
            {
                return filter.apply(input.status());
            }
        };
    }

    public Function<RemoteSlot, SlotStatus> getSlotStatus()
    {
        return new Function<RemoteSlot, SlotStatus>()
        {
            @Override
            public SlotStatus apply(RemoteSlot slot)
            {
                return slot.status();
            }
        };
    }
}
