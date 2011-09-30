package com.proofpoint.galaxy.coordinator;

import com.google.common.base.Function;
import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.base.Ticker;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.MapMaker;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.inject.Inject;
import com.proofpoint.galaxy.shared.AgentStatus;
import com.proofpoint.galaxy.shared.Assignment;
import com.proofpoint.galaxy.shared.Installation;
import com.proofpoint.galaxy.shared.SlotLifecycleState;
import com.proofpoint.galaxy.shared.SlotStatus;
import com.proofpoint.galaxy.shared.UpgradeVersions;
import com.proofpoint.units.Duration;

import javax.annotation.PostConstruct;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static com.google.common.base.Preconditions.checkNotNull;
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

    private final BinaryUrlResolver binaryUrlResolver;
    private final ConfigRepository configRepository;
    private final LocalConfigRepository localConfigRepository;
    private ScheduledExecutorService timerService;
    private final Duration statusExpiration;
    private final Ticker ticker;

    @Inject
    public Coordinator(CoordinatorConfig config,
            final RemoteAgentFactory remoteAgentFactory,
            BinaryUrlResolver binaryUrlResolver,
            ConfigRepository configRepository,
            LocalConfigRepository localConfigRepository)
    {
        this(remoteAgentFactory,
                binaryUrlResolver,
                configRepository,
                localConfigRepository,
                checkNotNull(config, "config is null").getStatusExpiration(),
                Ticker.systemTicker()
        );
    }

    public Coordinator(final RemoteAgentFactory remoteAgentFactory,
            BinaryUrlResolver binaryUrlResolver,
            ConfigRepository configRepository,
            LocalConfigRepository localConfigRepository,
            Duration statusExpiration,
            Ticker ticker)
    {
        Preconditions.checkNotNull(remoteAgentFactory, "remoteAgentFactory is null");
        Preconditions.checkNotNull(configRepository, "repository is null");
        Preconditions.checkNotNull(binaryUrlResolver, "binaryUrlResolver is null");
        Preconditions.checkNotNull(localConfigRepository, "localConfigRepository is null");
        Preconditions.checkNotNull(statusExpiration, "statusExpiration is null");
        Preconditions.checkNotNull(ticker, "ticker is null");

        this.binaryUrlResolver = binaryUrlResolver;
        this.configRepository = configRepository;
        this.localConfigRepository = localConfigRepository;
        this.statusExpiration = statusExpiration;
        this.ticker = ticker;

        agents = new MapMaker().makeComputingMap(new Function<UUID, RemoteAgent>()
        {
            public RemoteAgent apply(UUID agentId)
            {
                return remoteAgentFactory.createRemoteAgent(agentId);
            }
        });

        timerService = Executors.newSingleThreadScheduledExecutor(new ThreadFactoryBuilder().setNameFormat("coordinator-agent-monitor").setDaemon(true).build());
    }

    @PostConstruct
    public void start() {
        // check for expiration often enough that timeouts are not exceeded by more than 20%
        timerService.scheduleWithFixedDelay(new Runnable()
        {
            @Override
            public void run()
            {
                checkAgentStatuses();
            }
        }, 0, (long) statusExpiration.toMillis() / 5, TimeUnit.MILLISECONDS);
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

    public void checkAgentStatuses()
    {
        long minUpdateTime = (long) (ticker.read() - statusExpiration.convertTo(TimeUnit.NANOSECONDS));
        for (RemoteAgent remoteAgent : agents.values()) {
            if (remoteAgent.getLastUpdateTimestamp() < minUpdateTime) {
                remoteAgent.markAgentOffline();
            }
        }
    }

    public void updateAgentStatus(AgentStatus status)
    {
        agents.get(status.getAgentId()).updateStatus(status);
    }

    public boolean markAgentOffline(UUID agentId)
    {
        if (!agents.containsKey(agentId)) {
            return false;
        }
        agents.get(agentId).markAgentOffline();
        return true;
    }

    public boolean removeAgent(UUID agentId)
    {
        return agents.remove(agentId) != null;
    }

    public List<SlotStatus> install(Predicate<AgentStatus> filter, int limit, Assignment assignment)
    {
        Map<String,URI> configMap = localConfigRepository.getConfigMap(assignment.getConfig());
        if (configMap == null) {
            configMap = configRepository.getConfigMap(assignment.getConfig());
        }

        Installation installation = new Installation(assignment, binaryUrlResolver.resolve(assignment.getBinary()), configMap);

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

        final Installation installation = new Installation(assignment, binaryUrlResolver.resolve(assignment.getBinary()), configMap);

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
