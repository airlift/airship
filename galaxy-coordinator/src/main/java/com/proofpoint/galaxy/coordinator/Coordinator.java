package com.proofpoint.galaxy.coordinator;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.base.Objects;
import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.common.collect.MapMaker;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.common.io.InputSupplier;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.inject.Inject;
import com.proofpoint.discovery.client.ServiceDescriptor;
import com.proofpoint.galaxy.shared.AgentLifecycleState;
import com.proofpoint.galaxy.shared.AgentStatus;
import com.proofpoint.galaxy.shared.Assignment;
import com.proofpoint.galaxy.shared.ConfigRepository;
import com.proofpoint.galaxy.shared.ExpectedSlotStatus;
import com.proofpoint.galaxy.shared.Installation;
import com.proofpoint.galaxy.shared.SlotLifecycleState;
import com.proofpoint.galaxy.shared.SlotStatus;
import com.proofpoint.galaxy.shared.SlotStatusWithExpectedState;
import com.proofpoint.galaxy.shared.UpgradeVersions;
import com.proofpoint.log.Logger;
import com.proofpoint.node.NodeInfo;
import com.proofpoint.units.Duration;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.TreeMap;
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
import static com.proofpoint.galaxy.shared.ConfigUtils.newConfigEntrySupplier;
import static com.proofpoint.galaxy.shared.SlotLifecycleState.RESTARTING;
import static com.proofpoint.galaxy.shared.SlotLifecycleState.RUNNING;
import static com.proofpoint.galaxy.shared.SlotLifecycleState.STOPPED;
import static com.proofpoint.galaxy.shared.SlotLifecycleState.TERMINATED;
import static com.proofpoint.galaxy.shared.SlotLifecycleState.UNKNOWN;

public class Coordinator
{
    private static final Logger log = Logger.get(Coordinator.class);
    private final ConcurrentMap<String, RemoteAgent> agents;

    private final String environment;
    private final BinaryRepository binaryRepository;
    private final ConfigRepository configRepository;
    private final ScheduledExecutorService timerService;
    private final Duration statusExpiration;
    private final Provisioner provisioner;
    private final RemoteAgentFactory remoteAgentFactory;
    private final ServiceInventory serviceInventory;
    private final StateManager stateManager;

    @Inject
    public Coordinator(NodeInfo nodeInfo,
            CoordinatorConfig config,
            RemoteAgentFactory remoteAgentFactory,
            BinaryRepository binaryRepository,
            ConfigRepository configRepository,
            Provisioner provisioner,
            StateManager stateManager, ServiceInventory serviceInventory)
    {
        this(nodeInfo.getEnvironment(),
                remoteAgentFactory,
                binaryRepository,
                configRepository,
                provisioner,
                stateManager,
                serviceInventory,
                checkNotNull(config, "config is null").getStatusExpiration()
        );
    }

    public Coordinator(String environment,
            RemoteAgentFactory remoteAgentFactory,
            BinaryRepository binaryRepository,
            ConfigRepository configRepository,
            Provisioner provisioner,
            StateManager stateManager,
            ServiceInventory serviceInventory,
            Duration statusExpiration)
    {
        Preconditions.checkNotNull(environment, "environment is null");
        Preconditions.checkNotNull(remoteAgentFactory, "remoteAgentFactory is null");
        Preconditions.checkNotNull(configRepository, "repository is null");
        Preconditions.checkNotNull(binaryRepository, "binaryRepository is null");
        Preconditions.checkNotNull(provisioner, "provisioner is null");
        Preconditions.checkNotNull(stateManager, "stateManager is null");
        Preconditions.checkNotNull(serviceInventory, "serviceInventory is null");
        Preconditions.checkNotNull(statusExpiration, "statusExpiration is null");

        this.environment = environment;
        this.remoteAgentFactory = remoteAgentFactory;
        this.binaryRepository = binaryRepository;
        this.configRepository = configRepository;
        this.provisioner = provisioner;
        this.stateManager = stateManager;
        this.serviceInventory = serviceInventory;
        this.statusExpiration = statusExpiration;

        agents = new MapMaker().makeMap();

        timerService = Executors.newSingleThreadScheduledExecutor(new ThreadFactoryBuilder().setNameFormat("coordinator-agent-monitor").setDaemon(true).build());

        updateAllAgents();
    }

    @PostConstruct
    public void start()
    {
        timerService.scheduleWithFixedDelay(new Runnable()
        {
            @Override
            public void run()
            {
                try {
                    updateAllAgents();
                }
                catch (Throwable e) {
                    log.error(e, "Unexpected exception updating agents");
                }
            }
        }, 0, (long) statusExpiration.toMillis(), TimeUnit.MILLISECONDS);
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

    public List<AgentStatus> getAllAgents()
    {
        ImmutableList.Builder<AgentStatus> builder = ImmutableList.builder();
        for (RemoteAgent remoteAgent : agents.values()) {
            builder.add(remoteAgent.status());
        }
        return builder.build();
    }

    public List<AgentStatus> getAgents(Predicate<AgentStatus> agentFilter)
    {
        ImmutableList.Builder<AgentStatus> builder = ImmutableList.builder();
        for (RemoteAgent remoteAgent : filter(this.agents.values(), filterAgentsBy(agentFilter))) {
            builder.add(remoteAgent.status());
        }
        return builder.build();
    }

    public AgentStatus getAgentStatus(String agentId)
    {
        RemoteAgent agent = agents.get(agentId);
        if (agent == null) {
            return null;
        }
        return agent.status();
    }

    @VisibleForTesting
    public void updateAllAgents()
    {
        for (Instance instance : this.provisioner.listAgents()) {
            RemoteAgent remoteAgent = remoteAgentFactory.createRemoteAgent(instance.getInstanceId(), instance.getInstanceType(), instance.getUri());
            RemoteAgent existing = agents.putIfAbsent(instance.getInstanceId(), remoteAgent);
            if (existing != null) {
                existing.setUri(instance.getUri());
            }
        }

        List<ServiceDescriptor> serviceDescriptors = serviceInventory.getServiceInventory(transform(getAllSlots(), getSlotStatus()));
        for (RemoteAgent remoteAgent : agents.values()) {
            remoteAgent.updateStatus();
            remoteAgent.setServiceInventory(serviceDescriptors);
        }
    }

    @VisibleForTesting
    public void setAgentStatus(AgentStatus status)
    {

        RemoteAgent remoteAgent = agents.get(status.getAgentId());
        if (remoteAgent == null) {
            remoteAgent = remoteAgentFactory.createRemoteAgent(status.getAgentId(), status.getInstanceType(), status.getUri());
            agents.put(status.getAgentId(), remoteAgent);
        }
        remoteAgent.setStatus(status);
    }

    public List<AgentStatus> addAgents(int count, String instanceType, String availabilityZone)
            throws Exception
    {
        List<Instance> instances = provisioner.provisionAgents(count, instanceType, availabilityZone);

        List<AgentStatus> agents = newArrayList();
        for (Instance instance : instances) {
            String instanceId = instance.getInstanceId();

            AgentStatus agentStatus = new AgentStatus(
                    instanceId,
                    AgentLifecycleState.PROVISIONING,
                    null,
                    instance.getLocation(),
                    instance.getInstanceType(),
                    ImmutableList.<SlotStatus>of(),
                    ImmutableMap.<String, Integer>of());

            RemoteAgent remoteAgent = remoteAgentFactory.createRemoteAgent(instanceId, instance.getInstanceType(), null);
            remoteAgent.setStatus(agentStatus);
            this.agents.put(instanceId, remoteAgent);

            agents.add(agentStatus);
        }
        return agents;
    }

    @VisibleForTesting
    public boolean removeAgent(String agentId)
    {
        return agents.remove(agentId) != null;
    }

    public AgentStatus terminateAgent(String agentId)
    {
        RemoteAgent agent = agents.remove(agentId);
        if (agent == null) {
            return null;
        }
        if (!agent.getSlots().isEmpty()) {
            agents.putIfAbsent(agentId, agent);
            throw new IllegalStateException("Cannot terminate agent that has slots: " + agentId);
        }
        provisioner.terminateAgents(ImmutableList.of(agentId));
        return agent.status().updateState(AgentLifecycleState.TERMINATED);
    }

    public List<SlotStatus> install(Predicate<AgentStatus> filter, int limit, Assignment assignment)
    {
        URI configFile= configRepository.getConfigFile(assignment.getConfig());
        Map<String, Integer> resources = readResources(assignment);

        assignment = new Assignment(binaryRepository.resolveBinarySpec(assignment.getBinary()), assignment.getConfig());
        Installation installation = new Installation(assignment, binaryRepository.getBinaryUri(assignment.getBinary()), configFile, resources);

        List<SlotStatus> slots = newArrayList();
        List<RemoteAgent> agents = newArrayList(filter(this.agents.values(), Predicates.and(filterAgentsBy(filter), filterAgentsWithAssignment(assignment))));

        // randomize agents so all processes don't end up on the same node
        // todo sort agents by number of process already installed on them
        Collections.shuffle(agents);
        for (RemoteAgent agent : agents) {
            if (slots.size() >= limit) {
                break;
            }

            // verify agent state
            AgentStatus status = agent.status();
            if (status.getState() != ONLINE) {
                continue;
            }

            // verify that required resources are available
            Map<String, Integer> availableResources = getAvailableResources(status);
            if (!resourcesAreAvailable(availableResources, installation.getResources())) {
                continue;
            }

            // install
            SlotStatus slotStatus = agent.install(installation);
            stateManager.setExpectedState(new ExpectedSlotStatus(slotStatus.getId(), STOPPED, installation.getAssignment()));
            slots.add(slotStatus);
        }
        return ImmutableList.copyOf(slots);
    }

    private boolean resourcesAreAvailable(Map<String, Integer> availableResources, Map<String, Integer> requiredResources)
    {
        for (Entry<String, Integer> entry : requiredResources.entrySet()) {
            int available = Objects.firstNonNull(availableResources.get(entry.getKey()), 0);
            if (available < entry.getValue()) {
                return false;
            }
        }
        return true;
    }

    public static Map<String, Integer> getAvailableResources(AgentStatus agentStatus)
    {
        Map<String,Integer> availableResources = new TreeMap<String, Integer>(agentStatus.getResources());
        for (SlotStatus slotStatus : agentStatus.getSlotStatuses()) {
            for (Entry<String, Integer> entry : slotStatus.getResources().entrySet()) {
                int value = Objects.firstNonNull(availableResources.get(entry.getKey()), 0);
                availableResources.put(entry.getKey(), value - entry.getValue());
            }
        }
        return availableResources;
    }


    private Map<String, Integer> readResources(Assignment assignment)
    {
        ImmutableMap.Builder<String, Integer> builder = ImmutableMap.builder();

        InputSupplier<? extends InputStream> resourcesFile = newConfigEntrySupplier(configRepository, environment, assignment.getConfig(), "galaxy-resources.properties");
        if (resourcesFile != null) {
            try {
                Properties resources = new Properties();
                resources.load(resourcesFile.getInput());
                for (Entry<Object, Object> entry : resources.entrySet()) {
                    builder.put((String) entry.getKey(), Integer.valueOf((String)entry.getValue()));
                }
            }
            catch (IOException ignored) {
            }
        }
        return builder.build();
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

        URI configFile = configRepository.getConfigFile(assignment.getConfig());

        final Installation installation = new Installation(assignment, binaryRepository.getBinaryUri(assignment.getBinary()), configFile, ImmutableMap.<String, Integer>of());

        return ImmutableList.copyOf(transform(slotsToUpgrade, new Function<RemoteSlot, SlotStatus>()
        {
            @Override
            public SlotStatus apply(RemoteSlot slot)
            {
                SlotStatus slotStatus = slot.assign(installation);
                stateManager.setExpectedState(new ExpectedSlotStatus(slotStatus.getId(), STOPPED, installation.getAssignment()));
                return slotStatus;
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
                    SlotStatus slotStatus = slot.terminate();
                    if (slotStatus.getState() == TERMINATED) {
                        stateManager.deleteExpectedState(slotStatus.getId());
                    }
                    builder.add(slotStatus);
                }
            }
        }
        return builder.build();
    }

    public List<SlotStatus> setState(final SlotLifecycleState state, Predicate<SlotStatus> filter)
    {
        Preconditions.checkArgument(EnumSet.of(RUNNING, RESTARTING, STOPPED).contains(state), "Unsupported lifecycle state: " + state);

        return ImmutableList.copyOf(transform(filter(getAllSlots(), filterSlotsBy(filter)), new Function<RemoteSlot, SlotStatus>()
        {
            @Override
            public SlotStatus apply(RemoteSlot slot)
            {
                SlotStatus slotStatus;
                SlotLifecycleState expectedState;
                switch (state) {
                    case RUNNING:
                        slotStatus = slot.start();
                        expectedState = RUNNING;
                        break;
                    case RESTARTING:
                        slotStatus = slot.restart();
                        expectedState = RUNNING;
                        break;
                    case STOPPED:
                        slotStatus = slot.stop();
                        expectedState = STOPPED;
                        break;
                    default:
                        throw new IllegalArgumentException("Unexpected state transition " + state);
                }
                stateManager.setExpectedState(new ExpectedSlotStatus(slotStatus.getId(), expectedState, slotStatus.getAssignment()));
                return slotStatus;
            }
        }));
    }

    public List<SlotStatus> resetExpectedState(Predicate<SlotStatus> filter)
    {
        return ImmutableList.copyOf(transform(filter(getAllSlotStatus(), filter), new Function<SlotStatus, SlotStatus>()
        {
            @Override
            public SlotStatus apply(SlotStatus slotStatus)
            {
                if (slotStatus.getState() != SlotLifecycleState.UNKNOWN) {
                    stateManager.setExpectedState(new ExpectedSlotStatus(slotStatus.getId(), slotStatus.getState(), slotStatus.getAssignment()));
                }
                else {
                    stateManager.deleteExpectedState(slotStatus.getId());
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
        ImmutableMap<UUID, ExpectedSlotStatus> expectedStates = Maps.uniqueIndex(stateManager.getAllExpectedStates(), ExpectedSlotStatus.uuidGetter());
        ImmutableMap<UUID, SlotStatus> actualStates = Maps.uniqueIndex(transform(getAllSlots(), getSlotStatus()), SlotStatus.uuidGetter());

        ArrayList<SlotStatus> stats = newArrayList();
        for (UUID uuid : Sets.union(actualStates.keySet(), expectedStates.keySet())) {
            SlotStatus actualState = actualStates.get(uuid);
            ExpectedSlotStatus expectedState = expectedStates.get(uuid);
            if (actualState == null) {
                actualState = new SlotStatus(uuid, "unknown", null, "unknown", UNKNOWN, expectedState.getAssignment(), null, ImmutableMap.<String, Integer>of());
            }
            if (slotFilter.apply(actualState)) {
                stats.add(actualState);
            }
        }
        return ImmutableList.copyOf(stats);
    }

    public List<SlotStatusWithExpectedState> getAllSlotStatusWithExpectedState(Predicate<SlotStatus> slotFilter)
    {
        ImmutableMap<UUID, ExpectedSlotStatus> expectedStates = Maps.uniqueIndex(stateManager.getAllExpectedStates(), ExpectedSlotStatus.uuidGetter());
        ImmutableMap<UUID, SlotStatus> actualStates = Maps.uniqueIndex(transform(getAllSlots(), getSlotStatus()), SlotStatus.uuidGetter());

        ArrayList<SlotStatusWithExpectedState> stats = newArrayList();
        for (UUID uuid : Sets.union(actualStates.keySet(), expectedStates.keySet())) {
            SlotStatus actualState = actualStates.get(uuid);
            ExpectedSlotStatus expectedState = expectedStates.get(uuid);
            if (actualState == null) {
                // skip terminated slots
                if (expectedState == null || expectedState.getStatus() == SlotLifecycleState.TERMINATED)  {
                    continue;
                }
                actualState = new SlotStatus(uuid, "unknown", null, "unknown", UNKNOWN, expectedState.getAssignment(), null, ImmutableMap.<String, Integer>of());
                actualState = actualState.updateState(UNKNOWN, "Slot is missing; Expected slot to be " + expectedState.getStatus());
            }
            else if (expectedState == null) {
                actualState = actualState.updateState(actualState.getState(), "Unexpected slot");
            }
            else {
                List<String> messages = newArrayList();
                if (!Objects.equal(actualState.getState(), expectedState.getStatus())) {
                    messages.add("Expected state to be " + expectedState.getStatus());
                }
                if (!Objects.equal(actualState.getAssignment(), expectedState.getAssignment())) {
                    Assignment assignment = expectedState.getAssignment();
                    if (assignment != null) {
                        messages.add("Expected assignment to be " + assignment.getBinary() + " " + assignment.getConfig());
                    }
                    else {
                        messages.add("Expected no assignment");
                    }
                }
                if (!messages.isEmpty()) {
                    actualState = actualState.updateState(actualState.getState(), Joiner.on("; ").join(messages));
                }
            }
            if (slotFilter.apply(actualState)) {
                stats.add(new SlotStatusWithExpectedState(actualState, expectedState));
            }
        }

        return stats;
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

    private Predicate<RemoteAgent> filterAgentsWithAssignment(final Assignment assignment)
    {
        return new Predicate<RemoteAgent>()
        {
            @Override
            public boolean apply(RemoteAgent agent)
            {
                for (RemoteSlot slot : agent.getSlots()) {
                    if (assignment.equalsIgnoreVersion(slot.status().getAssignment())) {
                        return false;
                    }
                }
                return true;
            }
        };
    }
}
