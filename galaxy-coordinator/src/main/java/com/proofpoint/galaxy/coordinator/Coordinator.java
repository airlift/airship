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
import com.google.common.collect.ImmutableMap.Builder;
import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.inject.Inject;
import com.proofpoint.discovery.client.ServiceDescriptor;
import com.proofpoint.galaxy.shared.AgentLifecycleState;
import com.proofpoint.galaxy.shared.AgentStatus;
import com.proofpoint.galaxy.shared.Assignment;
import com.proofpoint.galaxy.shared.CoordinatorLifecycleState;
import com.proofpoint.galaxy.shared.CoordinatorStatus;
import com.proofpoint.galaxy.shared.ExpectedSlotStatus;
import com.proofpoint.galaxy.shared.Installation;
import com.proofpoint.galaxy.shared.InstallationUtils;
import com.proofpoint.galaxy.shared.Repository;
import com.proofpoint.galaxy.shared.SlotLifecycleState;
import com.proofpoint.galaxy.shared.SlotStatus;
import com.proofpoint.galaxy.shared.UpgradeVersions;
import com.proofpoint.log.Logger;
import com.proofpoint.node.NodeInfo;
import com.proofpoint.units.Duration;

import javax.annotation.PostConstruct;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.collect.Iterables.concat;
import static com.google.common.collect.Iterables.filter;
import static com.google.common.collect.Iterables.transform;
import static com.google.common.collect.Lists.newArrayList;
import static com.google.common.collect.Sets.newHashSet;
import static com.proofpoint.galaxy.shared.AgentLifecycleState.ONLINE;
import static com.proofpoint.galaxy.shared.SlotLifecycleState.RESTARTING;
import static com.proofpoint.galaxy.shared.SlotLifecycleState.RUNNING;
import static com.proofpoint.galaxy.shared.SlotLifecycleState.STOPPED;
import static com.proofpoint.galaxy.shared.SlotLifecycleState.TERMINATED;
import static com.proofpoint.galaxy.shared.SlotLifecycleState.UNKNOWN;
import static com.proofpoint.galaxy.shared.VersionsUtil.checkSlotsVersion;

public class Coordinator
{
    private static final Logger log = Logger.get(Coordinator.class);

    private final ConcurrentMap<String, CoordinatorStatus> coordinators = new ConcurrentHashMap<String, CoordinatorStatus>();
    private final ConcurrentMap<String, RemoteAgent> agents = new ConcurrentHashMap<String, RemoteAgent>();

    private final String environment;
    private final Repository repository;
    private final ScheduledExecutorService timerService;
    private final Duration statusExpiration;
    private final Provisioner provisioner;
    private final RemoteAgentFactory remoteAgentFactory;
    private final ServiceInventory serviceInventory;
    private final StateManager stateManager;
    private final boolean allowDuplicateInstallationsOnAnAgent;

    @Inject
    public Coordinator(NodeInfo nodeInfo,
            CoordinatorConfig config,
            RemoteAgentFactory remoteAgentFactory,
            Repository repository,
            Provisioner provisioner,
            StateManager stateManager, ServiceInventory serviceInventory)
    {
        this(nodeInfo.getEnvironment(),
                remoteAgentFactory,
                repository,
                provisioner,
                stateManager,
                serviceInventory,
                checkNotNull(config, "config is null").getStatusExpiration(),
                false);
    }

    public Coordinator(String environment,
            RemoteAgentFactory remoteAgentFactory,
            Repository repository,
            Provisioner provisioner,
            StateManager stateManager,
            ServiceInventory serviceInventory,
            Duration statusExpiration,
            boolean allowDuplicateInstallationsOnAnAgent)
    {
        Preconditions.checkNotNull(environment, "environment is null");
        Preconditions.checkNotNull(remoteAgentFactory, "remoteAgentFactory is null");
        Preconditions.checkNotNull(repository, "repository is null");
        Preconditions.checkNotNull(provisioner, "provisioner is null");
        Preconditions.checkNotNull(stateManager, "stateManager is null");
        Preconditions.checkNotNull(serviceInventory, "serviceInventory is null");
        Preconditions.checkNotNull(statusExpiration, "statusExpiration is null");

        this.environment = environment;
        this.remoteAgentFactory = remoteAgentFactory;
        this.repository = repository;
        this.provisioner = provisioner;
        this.stateManager = stateManager;
        this.serviceInventory = serviceInventory;
        this.statusExpiration = statusExpiration;
        this.allowDuplicateInstallationsOnAnAgent = allowDuplicateInstallationsOnAnAgent;

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
                    updateAllCoordinators();
                }
                catch (Throwable e) {
                    log.error(e, "Unexpected exception updating coordinators");
                }
                try {
                    updateAllAgents();
                }
                catch (Throwable e) {
                    log.error(e, "Unexpected exception updating agents");
                }
            }
        }, 0, (long) statusExpiration.toMillis(), TimeUnit.MILLISECONDS);
    }

    public String getEnvironment()
    {
        return environment;
    }

    public List<CoordinatorStatus> getCoordinators(Predicate<CoordinatorStatus> coordinatorFilter)
    {
        return ImmutableList.copyOf(filter(this.coordinators.values(), coordinatorFilter));
    }

    public List<CoordinatorStatus> provisionCoordinators(String coordinatorConfigSpec,
            int coordinatorCount,
            String instanceType,
            String availabilityZone,
            String ami,
            String keyPair,
            String securityGroup)
    {
        List<Instance> instances = provisioner.provisionCoordinators(coordinatorConfigSpec,
                coordinatorCount,
                instanceType,
                availabilityZone,
                ami,
                keyPair,
                securityGroup);

        List<CoordinatorStatus> coordinators = newArrayList();
        for (Instance instance : instances) {
            String instanceId = instance.getInstanceId();

            CoordinatorStatus coordinatorStatus = new CoordinatorStatus(
                    instanceId,
                    CoordinatorLifecycleState.PROVISIONING,
                    null,
                    null,
                    instance.getLocation(),
                    instance.getInstanceType());

            this.coordinators.put(instanceId, coordinatorStatus);
            coordinators.add(coordinatorStatus);
        }
        return coordinators;
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
        Iterable<RemoteAgent> remoteAgents = filter(this.agents.values(), filterAgentsBy(agentFilter));
        List<AgentStatus> agentStatuses = ImmutableList.copyOf(transform(remoteAgents, getAgentStatus()));
        return agentStatuses;
    }

    public AgentStatus getAgentStatus(String agentId)
    {
        for (RemoteAgent remoteAgent : agents.values()) {
            AgentStatus status = remoteAgent.status();
            if (agentId.equals(status.getAgentId())) {
                return status;
            }
        }
        return null;
    }

    @VisibleForTesting
    public void updateAllCoordinators()
    {
        Builder<String,CoordinatorStatus> builder = ImmutableMap.builder();
        for (Instance instance : this.provisioner.listCoordinators()) {
            // todo machine or process may still be starting (provisioning)
            CoordinatorStatus coordinatorStatus = new CoordinatorStatus(instance.getInstanceId(),
                    CoordinatorLifecycleState.ONLINE,
                    instance.getInternalUri(),
                    instance.getExternalUri(),
                    instance.getLocation(),
                    instance.getInstanceType());

            // todo remove terminated coordinators?
            coordinators.put(instance.getInstanceId(), coordinatorStatus);
            builder.put(coordinatorStatus.getCoordinatorId(), coordinatorStatus);
        }
    }

    @VisibleForTesting
    public void updateAllAgents()
    {
        Set<String> instanceIds = newHashSet();
        for (Instance instance : this.provisioner.listAgents()) {
            instanceIds.add(instance.getInstanceId());
            RemoteAgent remoteAgent = remoteAgentFactory.createRemoteAgent(instance);
            RemoteAgent existing = agents.putIfAbsent(instance.getInstanceId(), remoteAgent);
            if (existing != null) {
                if (existing.status().getState() == AgentLifecycleState.PROVISIONING) {
                    // replace the temporary provisioning instance with a real remote factory
                    agents.replace(instance.getInstanceId(), existing, remoteAgent);
                } else {
                    existing.setInternalUri(instance.getInternalUri());
                }
            }
        }
        for (RemoteAgent agent : agents.values()) {
            if (agent.status().getState() == AgentLifecycleState.PROVISIONING) {
                instanceIds.add(agent.status().getInstanceId());
            }
        }
        // remove any agents in the provisioner list
        agents.keySet().retainAll(instanceIds);

        List<ServiceDescriptor> serviceDescriptors = serviceInventory.getServiceInventory(transform(getAllSlots(), getSlotStatus()));
        for (RemoteAgent remoteAgent : agents.values()) {
            remoteAgent.updateStatus();
            remoteAgent.setServiceInventory(serviceDescriptors);
        }
    }

    public List<AgentStatus> provisionAgents(String agentConfigSpec,
            int agentCount,
            String instanceType,
            String availabilityZone,
            String ami,
            String keyPair,
            String securityGroup)
    {
        List<Instance> instances = provisioner.provisionAgents(agentConfigSpec,
                agentCount,
                instanceType,
                availabilityZone,
                ami,
                keyPair,
                securityGroup);

        List<AgentStatus> agents = newArrayList();
        for (Instance instance : instances) {
            String instanceId = instance.getInstanceId();

            RemoteAgent remoteAgent = new ProvisioningRemoteAgent(instance);
            this.agents.put(instanceId, remoteAgent);

            agents.add(remoteAgent.status());
        }
        return agents;
    }

    public AgentStatus terminateAgent(String agentId)
    {
        RemoteAgent agent = null;
        for (Iterator<Entry<String, RemoteAgent>> iterator = agents.entrySet().iterator(); iterator.hasNext(); ) {
            Entry<String, RemoteAgent> entry = iterator.next();
            if (entry.getValue().status().getAgentId().equals(agentId)) {
                iterator.remove();
                agent = entry.getValue();
                break;
            }
        }
        if (agent == null) {
            return null;
        }
        if (!agent.getSlots().isEmpty()) {
            agents.putIfAbsent(agent.status().getInstanceId(), agent);
            throw new IllegalStateException("Cannot terminate agent that has slots: " + agentId);
        }
        provisioner.terminateAgents(ImmutableList.of(agentId));
        return agent.status().changeState(AgentLifecycleState.TERMINATED);
    }

    public List<SlotStatus> install(Predicate<AgentStatus> filter, int limit, Assignment assignment)
    {
        Installation installation = InstallationUtils.toInstallation(repository, assignment);

        List<RemoteAgent> targetAgents = selectAgents(filter, installation);

        // randomize agents so all processes don't end up on the same node
        // todo sort agents by number of process already installed on them
        List<SlotStatus> slots = newArrayList();
        for (RemoteAgent agent : targetAgents) {
            if (slots.size() >= limit) {
                break;
            }

            // install
            SlotStatus slotStatus = agent.install(installation);
            stateManager.setExpectedState(new ExpectedSlotStatus(slotStatus.getId(), STOPPED, installation.getAssignment()));
            slots.add(slotStatus);
        }
        return ImmutableList.copyOf(slots);
    }

    private List<RemoteAgent> selectAgents(Predicate<AgentStatus> filter, Installation installation)
    {
        // randomize agents so all processes don't end up on the same node
        // todo sort agents by number of process already installed on them
        List<RemoteAgent> targetAgents = newArrayList();
        List<RemoteAgent> allAgents = newArrayList(filter(this.agents.values(), filterAgentsBy(filter)));
        if (!allowDuplicateInstallationsOnAnAgent) {
            allAgents = newArrayList(filter(this.agents.values(), filterAgentsWithAssignment(installation)));
        }
        Collections.shuffle(allAgents);
        for (RemoteAgent agent : allAgents) {
            // verify agent state
            AgentStatus status = agent.status();
            if (status.getState() != ONLINE) {
                continue;
            }

            // verify that required resources are available
            Map<String, Integer> availableResources = InstallationUtils.getAvailableResources(status);
            if (!InstallationUtils.resourcesAreAvailable(availableResources, installation.getResources())) {
                continue;
            }

            targetAgents.add(agent);
        }
        return targetAgents;
    }

    public List<SlotStatus> upgrade(Predicate<SlotStatus> filter, UpgradeVersions upgradeVersions, String expectedSlotsVersion)
    {
        // filter the slots
        List<RemoteSlot> filteredSlots = ImmutableList.copyOf(filter(getAllSlots(), filterSlotsBy(filter)));

        // verify the state of the system hasn't changed
        checkSlotsVersion(expectedSlotsVersion, transform(filteredSlots, getSlotStatus()));

        HashSet<Assignment> newAssignments = new HashSet<Assignment>();
        List<RemoteSlot> slotsToUpgrade = new ArrayList<RemoteSlot>();
        for (RemoteSlot slot : filteredSlots) {
            SlotStatus status = slot.status();
            SlotLifecycleState state = status.getState();
            if (state != TERMINATED && state != UNKNOWN) {
                Assignment assignment = upgradeVersions.upgradeAssignment(repository, status.getAssignment());
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

        URI configFile = repository.configToHttpUri(assignment.getConfig());

        final Installation installation = new Installation(
                repository.configShortName(assignment.getConfig()),
                assignment,
                repository.binaryToHttpUri(assignment.getBinary()),
                configFile, ImmutableMap.<String, Integer>of());

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

    public List<SlotStatus> terminate(Predicate<SlotStatus> filter, String expectedSlotsVersion)
    {
        Preconditions.checkNotNull(filter, "filter is null");

        // filter the slots
        List<RemoteSlot> filteredSlots = ImmutableList.copyOf(filter(getAllSlots(), filterSlotsBy(filter)));

        // verify the state of the system hasn't changed
        checkSlotsVersion(expectedSlotsVersion, transform(filteredSlots, getSlotStatus()));

        ImmutableList.Builder<SlotStatus> builder = ImmutableList.builder();
        for (RemoteSlot slot : filteredSlots) {
            if (filter.apply(slot.status())) {
                SlotStatus slotStatus = slot.terminate();
                if (slotStatus.getState() == TERMINATED) {
                    stateManager.deleteExpectedState(slotStatus.getId());
                }
                builder.add(slotStatus);
            }
        }
        return builder.build();
    }

    public List<SlotStatus> setState(final SlotLifecycleState state, Predicate<SlotStatus> filter, String expectedSlotsVersion)
    {
        Preconditions.checkArgument(EnumSet.of(RUNNING, RESTARTING, STOPPED).contains(state), "Unsupported lifecycle state: " + state);

        // filter the slots
        List<RemoteSlot> filteredSlots = ImmutableList.copyOf(filter(getAllSlots(), filterSlotsBy(filter)));

        // verify the state of the system hasn't changed
        checkSlotsVersion(expectedSlotsVersion, transform(filteredSlots, getSlotStatus()));

        return ImmutableList.copyOf(transform(filteredSlots, new Function<RemoteSlot, SlotStatus>()
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

    public List<SlotStatus> resetExpectedState(Predicate<SlotStatus> filter, String expectedSlotsVersion)
    {
        // filter the slots
        List<SlotStatus> filteredSlots = ImmutableList.copyOf(transform(filter(getAllSlots(), filterSlotsBy(filter)), getSlotStatus()));

        // verify the state of the system hasn't changed
        checkSlotsVersion(expectedSlotsVersion, filteredSlots);

        return ImmutableList.copyOf(transform(filteredSlots, new Function<SlotStatus, SlotStatus>()
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
            final SlotStatus actualState = actualStates.get(uuid);
            final ExpectedSlotStatus expectedState = expectedStates.get(uuid);

            SlotStatus fullSlotStatus;
            if (actualState == null) {
                // skip terminated slots
                if (expectedState == null || expectedState.getStatus() == SlotLifecycleState.TERMINATED)  {
                    continue;
                }
                // missing slot
                fullSlotStatus = SlotStatus.createSlotStatusWithExpectedState(uuid,
                        "unknown",
                        null,
                        null,
                        "unknown",
                        UNKNOWN,
                        expectedState.getAssignment(),
                        null,
                        ImmutableMap.<String, Integer>of(), expectedState.getStatus(),
                        expectedState.getAssignment(),
                        "Slot is missing; Expected slot to be " + expectedState.getStatus());
            }
            else if (expectedState == null) {
                // unexpected slot
                fullSlotStatus = actualState.changeStatusMessage("Unexpected slot").changeExpectedState(null, null);
            }
            else {
                fullSlotStatus = actualState.changeExpectedState(expectedState.getStatus(), expectedState.getAssignment());

                // add error message if actual state doesn't match expected state
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
                    fullSlotStatus = fullSlotStatus.changeStatusMessage(Joiner.on("; ").join(messages));
                }
            }
            if (slotFilter.apply(fullSlotStatus)) {
                stats.add(fullSlotStatus);
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

    private List<RemoteSlot> getAllSlots()
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
    private Function<RemoteAgent, AgentStatus> getAgentStatus()
    {
        return new Function<RemoteAgent, AgentStatus>()
        {
            @Override
            public AgentStatus apply(RemoteAgent agent)
            {
                return agent.status();
            }
        };
    }

    private Predicate<RemoteAgent> filterAgentsWithAssignment(final Installation installation)
    {
        Preconditions.checkNotNull(installation, "installation is null");
        final Assignment assignment = installation.getAssignment();

        return new Predicate<RemoteAgent>()
        {
            @Override
            public boolean apply(RemoteAgent agent)
            {
                for (RemoteSlot slot : agent.getSlots()) {
                    if (repository.binaryEqualsIgnoreVersion(assignment.getBinary(), slot.status().getAssignment().getBinary()) &&
                            repository.configEqualsIgnoreVersion(assignment.getConfig(), slot.status().getAssignment().getConfig())) {
                        return false;
                    }
                }
                return true;
            }
        };
    }
}
