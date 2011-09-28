package com.proofpoint.galaxy.coordinator;

import com.google.common.base.Function;
import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.MapMaker;
import com.google.inject.Inject;
import com.proofpoint.galaxy.shared.AgentStatus;
import com.proofpoint.galaxy.shared.Assignment;
import com.proofpoint.galaxy.shared.Installation;
import com.proofpoint.galaxy.shared.SlotLifecycleState;
import com.proofpoint.galaxy.shared.SlotStatus;
import com.proofpoint.galaxy.shared.UpgradeVersions;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentMap;

import static com.google.common.base.Predicates.alwaysTrue;
import static com.google.common.collect.Iterables.concat;
import static com.google.common.collect.Iterables.filter;
import static com.google.common.collect.Iterables.transform;
import static com.google.common.collect.Lists.newArrayList;
import static com.proofpoint.galaxy.shared.AgentLifecycleState.ONLINE;
import static com.proofpoint.galaxy.shared.SlotLifecycleState.RUNNING;
import static com.proofpoint.galaxy.shared.SlotLifecycleState.STOPPED;
import static com.proofpoint.galaxy.shared.SlotLifecycleState.TERMINATED;
import static com.proofpoint.galaxy.shared.SlotLifecycleState.UNKNOWN;

public class Coordinator
{
    private final ConcurrentMap<UUID, RemoteAgent> agents;

    private final BinaryRepository binaryRepository;
    private final ConfigRepository configRepository;
    private final LocalConfigRepository localConfigRepository;

    @Inject
    public Coordinator(final RemoteAgentFactory remoteAgentFactory, BinaryRepository binaryRepository, ConfigRepository configRepository, LocalConfigRepository localConfigRepository)
    {
        Preconditions.checkNotNull(remoteAgentFactory, "remoteAgentFactory is null");
        Preconditions.checkNotNull(configRepository, "repository is null");
        Preconditions.checkNotNull(binaryRepository, "binaryRepository is null");
        Preconditions.checkNotNull(localConfigRepository, "localConfigRepository is null");

        this.binaryRepository = binaryRepository;
        this.configRepository = configRepository;
        this.localConfigRepository = localConfigRepository;

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

    public List<SlotStatus> install(Predicate<AgentStatus> filter, int limit, final Installation installation)
    {

        List<SlotStatus> slots = newArrayList();
        List<RemoteAgent> agents = newArrayList(filter(this.agents.values(), filterAgentsBy(filter)));
        // randomize agents so all processes don't end up on the same node
        // todo sort agents by number of process already installed on them
        Collections.shuffle(agents);
        for (RemoteAgent agent : agents) {
            if (slots.size() >= limit) {
                break;
            }
            if (agent.status().getState() == ONLINE) {
                slots.add(agent.install(installation));
            }
        }
        return ImmutableList.copyOf(slots);
    }


    public List<SlotStatus> upgrade(Predicate<SlotStatus> filter, UpgradeVersions upgradeVersions)
    {
        HashSet<Assignment> newAssignments = new HashSet<Assignment>();
        List<RemoteSlot> slotsToUpgrade = new ArrayList<RemoteSlot>();
        for (RemoteSlot slot : ImmutableList.copyOf(filter(getAllSlots(), filterSlotsBy(filter)))) {
            SlotStatus status = slot.status();
            SlotLifecycleState state = status.getState();
            if (state != TERMINATED && state != UNKNOWN) {
                Assignment assignment = upgradeVersions.upgradeAssignment(status.getAssignment());
                newAssignments.add(assignment);
                slotsToUpgrade.add(slot);
            }
        }

        // no slots to upgrade
        if (newAssignments.isEmpty()) {
            return ImmutableList.of();
        }

        // must upgrade to a single new version
        if (newAssignments.size() != 1) {
            throw new AmbiguousUpgradeException(newAssignments);
        }
        Assignment assignment = newAssignments.iterator().next();

        Map<String,URI> configMap = localConfigRepository.getConfigMap(assignment.getConfig());
        if (configMap == null) {
            configMap = configRepository.getConfigMap(assignment.getConfig());
        }

        final Installation installation = new Installation(assignment, binaryRepository.getBinaryUri(assignment.getBinary()), configMap);

        return ImmutableList.copyOf(transform(slotsToUpgrade, new Function<RemoteSlot, SlotStatus>()
        {
            @Override
            public SlotStatus apply(RemoteSlot slot)
            {
                return slot.assign(installation);
            }
        }));
    }

    public List<SlotStatus> terminate(Predicate<SlotStatus> filter)
    {
        Preconditions.checkNotNull(filter, "filter is null");

        ImmutableList.Builder<SlotStatus> builder = ImmutableList.builder();
        for (RemoteAgent agent : agents.values()) {
            for (RemoteSlot slot : agent.getSlots()) {
                if (filter.apply(slot.status())) {
                    SlotStatus slotStatus = agent.terminateSlot(slot.getId());
                    builder.add(slotStatus);
                }
            }
        }
        return builder.build();
    }

    public List<SlotStatus> setState(final SlotLifecycleState state, Predicate<SlotStatus> filter)
    {
        Preconditions.checkArgument(EnumSet.of(RUNNING, STOPPED).contains(state), "Unsupported lifecycle state: " + state);

        return ImmutableList.copyOf(transform(filter(getAllSlots(), filterSlotsBy(filter)), new Function<RemoteSlot, SlotStatus>()
        {
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

    public List<SlotStatus> getAllSlotStatus()
    {
        return getAllSlotsStatus(Predicates.<SlotStatus>alwaysTrue());
    }

    public List<SlotStatus> getAllSlotsStatus(Predicate<SlotStatus> slotFilter)
    {
        return ImmutableList.copyOf(filter(transform(getAllSlots(), getSlotStatus()), slotFilter));
    }

    private Predicate<RemoteSlot> filterSlotsBy(final Predicate<SlotStatus> filter)
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

    private List<? extends RemoteSlot> getAllSlots()
    {
        return ImmutableList.copyOf(concat(Iterables.transform(agents.values(), new Function<RemoteAgent, List<? extends RemoteSlot>>()
        {
            public List<? extends RemoteSlot> apply(RemoteAgent agent)
            {
                return agent.getSlots();
            }
        })));
    }

    private Predicate<RemoteAgent> filterAgentsBy(final Predicate<AgentStatus> filter)
    {
        return new Predicate<RemoteAgent>()
        {
            @Override
            public boolean apply(RemoteAgent input)
            {
                return filter.apply(input.status());
            }
        };
    }

    private Function<RemoteSlot, SlotStatus> getSlotStatus()
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
