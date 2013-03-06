package io.airlift.airship.coordinator;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.base.Objects;
import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimaps;
import com.google.common.collect.Sets;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.inject.Inject;
import io.airlift.airship.coordinator.AgentFilterBuilder.StatePredicate;
import io.airlift.airship.shared.AgentLifecycleState;
import io.airlift.airship.shared.AgentStatus;
import io.airlift.airship.shared.Assignment;
import io.airlift.airship.shared.CoordinatorLifecycleState;
import io.airlift.airship.shared.CoordinatorStatus;
import io.airlift.airship.shared.ExpectedSlotStatus;
import io.airlift.airship.shared.Installation;
import io.airlift.airship.shared.InstallationUtils;
import io.airlift.airship.shared.Repository;
import io.airlift.airship.shared.SlotLifecycleState;
import io.airlift.airship.shared.SlotStatus;
import io.airlift.airship.shared.UpgradeVersions;
import io.airlift.discovery.client.ServiceDescriptor;
import io.airlift.http.server.HttpServerInfo;
import io.airlift.log.Logger;
import io.airlift.node.NodeInfo;
import io.airlift.units.Duration;

import javax.annotation.Nullable;
import javax.annotation.PostConstruct;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.collect.Iterables.concat;
import static com.google.common.collect.Iterables.filter;
import static com.google.common.collect.Iterables.transform;
import static com.google.common.collect.Lists.newArrayList;
import static com.google.common.collect.Sets.newHashSet;
import static io.airlift.airship.shared.SlotLifecycleState.RESTARTING;
import static io.airlift.airship.shared.SlotLifecycleState.RUNNING;
import static io.airlift.airship.shared.SlotLifecycleState.STOPPED;
import static io.airlift.airship.shared.SlotLifecycleState.TERMINATED;
import static io.airlift.airship.shared.SlotLifecycleState.UNKNOWN;
import static io.airlift.airship.shared.VersionsUtil.checkSlotsVersion;

public class Coordinator
{
    private static final Logger log = Logger.get(Coordinator.class);

    private final ConcurrentMap<String, RemoteCoordinator> coordinators = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, RemoteAgent> agents = new ConcurrentHashMap<>();

    private final CoordinatorStatus coordinatorStatus;
    private final Repository repository;
    private final ScheduledExecutorService timerService;
    private final Duration statusExpiration;
    private final Provisioner provisioner;
    private final RemoteCoordinatorFactory remoteCoordinatorFactory;
    private final RemoteAgentFactory remoteAgentFactory;
    private final ServiceInventory serviceInventory;
    private final StateManager stateManager;
    private final boolean allowDuplicateInstallationsOnAnAgent;
    private final ExecutorService executor;

    @Inject
    public Coordinator(NodeInfo nodeInfo,
            HttpServerInfo httpServerInfo,
            CoordinatorConfig config,
            RemoteCoordinatorFactory remoteCoordinatorFactory,
            RemoteAgentFactory remoteAgentFactory,
            Repository repository,
            Provisioner provisioner,
            StateManager stateManager, ServiceInventory serviceInventory)
    {
        this(
                new CoordinatorStatus(nodeInfo.getInstanceId(),
                        CoordinatorLifecycleState.ONLINE,
                        nodeInfo.getInstanceId(),
                        httpServerInfo.getHttpUri(),
                        httpServerInfo.getHttpExternalUri(),
                        nodeInfo.getLocation(),
                        null),
                remoteCoordinatorFactory,
                remoteAgentFactory,
                repository,
                provisioner,
                stateManager,
                serviceInventory,
                checkNotNull(config, "config is null").getStatusExpiration(),
                config.isAllowDuplicateInstallationsOnAnAgent());
    }

    public Coordinator(CoordinatorStatus coordinatorStatus,
            RemoteCoordinatorFactory remoteCoordinatorFactory,
            RemoteAgentFactory remoteAgentFactory,
            Repository repository,
            Provisioner provisioner,
            StateManager stateManager,
            ServiceInventory serviceInventory,
            Duration statusExpiration,
            boolean allowDuplicateInstallationsOnAnAgent)
    {
        Preconditions.checkNotNull(coordinatorStatus, "coordinatorStatus is null");
        Preconditions.checkNotNull(remoteCoordinatorFactory, "remoteCoordinatorFactory is null");
        Preconditions.checkNotNull(remoteAgentFactory, "remoteAgentFactory is null");
        Preconditions.checkNotNull(repository, "repository is null");
        Preconditions.checkNotNull(provisioner, "provisioner is null");
        Preconditions.checkNotNull(stateManager, "stateManager is null");
        Preconditions.checkNotNull(serviceInventory, "serviceInventory is null");
        Preconditions.checkNotNull(statusExpiration, "statusExpiration is null");

        this.coordinatorStatus = coordinatorStatus;
        this.remoteCoordinatorFactory = remoteCoordinatorFactory;
        this.remoteAgentFactory = remoteAgentFactory;
        this.repository = repository;
        this.provisioner = provisioner;
        this.stateManager = stateManager;
        this.serviceInventory = serviceInventory;
        this.statusExpiration = statusExpiration;
        this.allowDuplicateInstallationsOnAnAgent = allowDuplicateInstallationsOnAnAgent;

        this.executor = Executors.newCachedThreadPool(new ThreadFactoryBuilder().setNameFormat("coordinator-task").build());

        timerService = Executors.newSingleThreadScheduledExecutor(new ThreadFactoryBuilder().setNameFormat("coordinator-agent-monitor").setDaemon(true).build());

        updateAllCoordinators();
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

    public CoordinatorStatus status()
    {
        return coordinatorStatus;
    }

    public CoordinatorStatus getCoordinator(String instanceId)
    {
        if (coordinatorStatus.getInstanceId().equals(instanceId)) {
            return status();
        }
        RemoteCoordinator remoteCoordinator = coordinators.get(instanceId);
        if (remoteCoordinator == null) {
            return null;
        }
        return remoteCoordinator.status();
    }

    public List<CoordinatorStatus> getCoordinators()
    {
        List<CoordinatorStatus> statuses = ImmutableList.copyOf(Iterables.transform(coordinators.values(), new Function<RemoteCoordinator, CoordinatorStatus>()
        {
            public CoordinatorStatus apply(RemoteCoordinator agent)
            {
                return agent.status();
            }
        }));

        return ImmutableList.<CoordinatorStatus>builder()
                .add(coordinatorStatus)
                .addAll(statuses)
                .build();
    }

    public List<CoordinatorStatus> getCoordinators(Predicate<CoordinatorStatus> coordinatorFilter)
    {
        return ImmutableList.copyOf(filter(getCoordinators(), coordinatorFilter));
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

            if (instanceId.equals(this.coordinatorStatus.getInstanceId())) {
                throw new IllegalStateException("Provisioner created a coordinator with the same is as this coordinator");
            }

            RemoteCoordinator remoteCoordinator = remoteCoordinatorFactory.createRemoteCoordinator(instance, CoordinatorLifecycleState.PROVISIONING);
            this.coordinators.put(instanceId, remoteCoordinator);

            coordinators.add(remoteCoordinator.status());
        }
        return coordinators;
    }

    public List<AgentStatus> getAgents()
    {
        return ImmutableList.copyOf(Iterables.transform(agents.values(), new Function<RemoteAgent, AgentStatus>()
        {
            public AgentStatus apply(RemoteAgent agent)
            {
                return agent.status();
            }
        }));
    }

    public List<AgentStatus> getAgents(Predicate<AgentStatus> agentFilter)
    {
        Iterable<RemoteAgent> remoteAgents = filter(this.agents.values(), filterAgentsBy(agentFilter));
        List<AgentStatus> agentStatuses = ImmutableList.copyOf(transform(remoteAgents, getAgentStatus()));
        return agentStatuses;
    }

    public AgentStatus getAgent(String instanceId)
    {
        RemoteAgent remoteAgent = agents.get(instanceId);
        if (remoteAgent == null) {
            return null;
        }
        return remoteAgent.status();
    }

    public AgentStatus getAgentByAgentId(String agentId)
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
        Set<String> instanceIds = newHashSet();
        for (Instance instance : this.provisioner.listCoordinators()) {
            instanceIds.add(instance.getInstanceId());

            // skip this server since it is automatically managed
            if (instance.getInstanceId().equals(this.coordinatorStatus.getInstanceId())) {
                continue;
            }

            RemoteCoordinator remoteCoordinator = remoteCoordinatorFactory.createRemoteCoordinator(instance, instance.getInternalUri() != null ? CoordinatorLifecycleState.ONLINE : CoordinatorLifecycleState.OFFLINE);
            RemoteCoordinator existing = coordinators.putIfAbsent(instance.getInstanceId(), remoteCoordinator);
            if (existing != null) {
                existing.setInternalUri(instance.getInternalUri());
            }
        }

        // add provisioning coordinators to provisioner list
        for (RemoteCoordinator remoteCoordinator : coordinators.values()) {
            if (remoteCoordinator.status().getState() == CoordinatorLifecycleState.PROVISIONING) {
                instanceIds.add(coordinatorStatus.getCoordinatorId());
            }
        }

        // remove any coordinators in the provisioner list
        coordinators.keySet().retainAll(instanceIds);

        for (RemoteCoordinator remoteCoordinator : coordinators.values()) {
            remoteCoordinator.updateStatus();
        }
    }

    @VisibleForTesting
    public void updateAllAgents()
    {
        Set<String> instanceIds = newHashSet();
        for (Instance instance : this.provisioner.listAgents()) {
            instanceIds.add(instance.getInstanceId());
            RemoteAgent remoteAgent = remoteAgentFactory.createRemoteAgent(instance, instance.getInternalUri() != null ? AgentLifecycleState.ONLINE : AgentLifecycleState.OFFLINE);
            RemoteAgent existing = agents.putIfAbsent(instance.getInstanceId(), remoteAgent);
            if (existing != null) {
                existing.setInternalUri(instance.getInternalUri());
            }
        }

        // add provisioning agents to provisioner list
        for (RemoteAgent remoteAgent : agents.values()) {
            if (remoteAgent.status().getState() == AgentLifecycleState.PROVISIONING) {
                instanceIds.add(remoteAgent.status().getAgentId());
            }

        }

        // remove any agents not in the provisioner list
        agents.keySet().retainAll(instanceIds);

        List<ListenableFuture<?>> futures = new ArrayList<>();
        List<ServiceDescriptor> serviceDescriptors = serviceInventory.getServiceInventory(transform(getAllSlots(), getSlotStatus()));
        for (RemoteAgent remoteAgent : agents.values()) {
            futures.add(remoteAgent.updateStatus());
            remoteAgent.setServiceInventory(serviceDescriptors);
        }
        waitForFutures(futures);
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

            RemoteAgent remoteAgent = remoteAgentFactory.createRemoteAgent(instance, AgentLifecycleState.PROVISIONING);
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
        final Installation installation = InstallationUtils.toInstallation(repository, assignment);

        List<RemoteAgent> targetAgents = new ArrayList<>(selectAgents(filter, installation));
        targetAgents = targetAgents.subList(0, Math.min(targetAgents.size(), limit));

        return parallel(targetAgents, new Function<RemoteAgent, SlotStatus>()
        {
            @Override
            public SlotStatus apply(RemoteAgent agent)
            {
                SlotStatus slotStatus = agent.install(installation);
                stateManager.setExpectedState(new ExpectedSlotStatus(slotStatus.getId(), STOPPED, installation.getAssignment()));
                return slotStatus;
            }
        });
    }

    private List<RemoteAgent> selectAgents(Predicate<AgentStatus> filter, Installation installation)
    {
        // select only online agents
        filter = Predicates.and(filter, new StatePredicate(AgentLifecycleState.ONLINE));
        List<RemoteAgent> allAgents = newArrayList(filter(this.agents.values(), filterAgentsBy(filter)));
        if (allAgents.isEmpty()) {
            throw new IllegalStateException("No online agents match the provided filters.");
        }
        if (!allowDuplicateInstallationsOnAnAgent) {
            allAgents = newArrayList(filter(allAgents, filterAgentsWithAssignment(installation)));
            if (allAgents.isEmpty()) {
                throw new IllegalStateException("All agents already have the specified binary and configuration installed.");
            }
        }

        // randomize agents so all processes don't end up on the same node
        // todo sort agents by number of process already installed on them?
        Collections.shuffle(allAgents);

        List<RemoteAgent> targetAgents = newArrayList();
        for (RemoteAgent agent : allAgents) {
            // agents without declared resources are considered to have unlimited resources
            AgentStatus status = agent.status();
            if (!status.getResources().isEmpty()) {
                // verify that required resources are available
                Map<String, Integer> availableResources = InstallationUtils.getAvailableResources(status);
                if (!InstallationUtils.resourcesAreAvailable(availableResources, installation.getResources())) {
                    continue;
                }
            }

            targetAgents.add(agent);
        }
        if (targetAgents.isEmpty()) {
            throw new IllegalStateException("No agents have the available resources to run the specified binary and configuration.");
        }
        return targetAgents;
    }

    public List<SlotStatus> upgrade(Predicate<SlotStatus> filter, UpgradeVersions upgradeVersions, String expectedSlotsVersion)
    {
        List<RemoteSlot> filteredSlots = selectRemoteSlots(filter, expectedSlotsVersion);

        final Map<UUID, Assignment> newAssignments = new HashMap<>();
        List<RemoteSlot> slotsToUpgrade = new ArrayList<>();
        for (RemoteSlot slot : filteredSlots) {
            SlotStatus status = slot.status();
            SlotLifecycleState state = status.getState();
            if (state != TERMINATED && state != UNKNOWN) {
                Assignment assignment = upgradeVersions.upgradeAssignment(repository, status.getAssignment());
                newAssignments.put(slot.getId(), assignment);
                slotsToUpgrade.add(slot);
            }
        }

        // no slots to upgrade
        if (newAssignments.isEmpty()) {
            return ImmutableList.of();
        }

        // assure that new assignments all have the same binary (ignoring version)
        if (!sameBinary(newAssignments.values())) {
            TreeSet<String> binaries = new TreeSet<>();
            for (RemoteSlot slot : filteredSlots) {
                binaries.add(slot.status().getAssignment().getBinary());
            }
            throw new IllegalArgumentException("Expected a target slots for upgrade command to have a single binary, but found: " + Joiner.on(", ").join(binaries));
        }

        return parallelCommand(slotsToUpgrade, new Function<RemoteSlot, SlotStatus>()
        {
            @Override
            public SlotStatus apply(RemoteSlot slot)
            {
                boolean expectRestart = slot.status().getState() == RUNNING;

                Assignment assignment = newAssignments.get(slot.getId());
                Preconditions.checkState(assignment != null, "Error no assignment for slot " + slot.getId());

                URI configFile = repository.configToHttpUri(assignment.getConfig());

                Installation installation = new Installation(
                        repository.configShortName(assignment.getConfig()),
                        assignment,
                        repository.binaryToHttpUri(assignment.getBinary()),
                        configFile, ImmutableMap.<String, Integer>of());

                stateManager.setExpectedState(new ExpectedSlotStatus(slot.getId(), expectRestart ? RUNNING : STOPPED, installation.getAssignment()));
                SlotStatus slotStatus = slot.assign(installation);
                return slotStatus;
            }
        }) ;
    }

    private boolean sameBinary(Collection<Assignment> values)
    {
        if (values.size() < 2) {
            return true;
        }
        final Assignment assignment = Iterables.getFirst(values, null);
        return Iterables.all(values, new Predicate<Assignment>() {
            @Override
            public boolean apply(@Nullable Assignment input)
            {
                return repository.binaryEqualsIgnoreVersion(input.getBinary(), assignment.getBinary());
            }
        });
    }

    public List<SlotStatus> terminate(Predicate<SlotStatus> filter, String expectedSlotsVersion)
    {
        Preconditions.checkNotNull(filter, "filter is null");

        // filter the slots
        List<RemoteSlot> filteredSlots = selectRemoteSlots(filter, expectedSlotsVersion);

        return parallelCommand(filteredSlots, new Function<RemoteSlot, SlotStatus>()
        {
            @Override
            public SlotStatus apply(RemoteSlot slot)
            {
                SlotStatus slotStatus = slot.terminate();
                if (slotStatus.getState() == TERMINATED) {
                    stateManager.deleteExpectedState(slotStatus.getId());
                }
                return slotStatus;
            }
        });
    }

    public List<SlotStatus> setState(final SlotLifecycleState state, Predicate<SlotStatus> filter, String expectedSlotsVersion)
    {
        Preconditions.checkArgument(EnumSet.of(RUNNING, RESTARTING, STOPPED).contains(state), "Unsupported lifecycle state: " + state);

        // filter the slots
        List<RemoteSlot> filteredSlots = selectRemoteSlots(filter, expectedSlotsVersion);

        return parallelCommand(filteredSlots, new Function<RemoteSlot, SlotStatus>()
        {
            @Override
            public SlotStatus apply(RemoteSlot slot)
            {
                switch (state) {
                    case RUNNING:
                        stateManager.setExpectedState(new ExpectedSlotStatus(slot.getId(), RUNNING, slot.status().getAssignment()));
                        return slot.start();
                    case RESTARTING:
                        stateManager.setExpectedState(new ExpectedSlotStatus(slot.getId(), RUNNING, slot.status().getAssignment()));
                        return slot.restart();
                    case STOPPED:
                        stateManager.setExpectedState(new ExpectedSlotStatus(slot.getId(), STOPPED, slot.status().getAssignment()));
                        return slot.stop();
                    default:
                        throw new IllegalArgumentException("Unexpected state transition " + state);
                }
            }
        });
    }

    public List<SlotStatus> resetExpectedState(Predicate<SlotStatus> filter, String expectedSlotsVersion)
    {
        // filter the slots
        List<SlotStatus> filteredSlots = getAllSlotsStatus(filter);

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

    private List<RemoteSlot> selectRemoteSlots(Predicate<SlotStatus> filter, String expectedSlotsVersion)
    {
        // filter the slots
        List<RemoteSlot> filteredSlots = ImmutableList.copyOf(filter(getAllSlots(), filterSlotsBy(filter)));

        // verify the state of the system hasn't changed
        checkSlotsVersion(expectedSlotsVersion, getAllSlotsStatus(filter, filteredSlots));

        return filteredSlots;
    }

    public List<SlotStatus> getAllSlotStatus()
    {
        return getAllSlotsStatus(Predicates.<SlotStatus>alwaysTrue());
    }

    public List<SlotStatus> getAllSlotsStatus(Predicate<SlotStatus> slotFilter)
    {
        return getAllSlotsStatus(slotFilter, getAllSlots());
    }

    private List<SlotStatus> getAllSlotsStatus(Predicate<SlotStatus> slotFilter, List<RemoteSlot> allSlots)
    {
        ImmutableMap<UUID, ExpectedSlotStatus> expectedStates = Maps.uniqueIndex(stateManager.getAllExpectedStates(), ExpectedSlotStatus.uuidGetter());
        ImmutableMap<UUID, SlotStatus> actualStates = Maps.uniqueIndex(transform(allSlots, getSlotStatus()), SlotStatus.uuidGetter());

        ArrayList<SlotStatus> stats = newArrayList();
        for (UUID uuid : Sets.union(actualStates.keySet(), expectedStates.keySet())) {
            final SlotStatus actualState = actualStates.get(uuid);
            final ExpectedSlotStatus expectedState = expectedStates.get(uuid);

            SlotStatus fullSlotStatus;
            if (actualState == null) {
                // skip terminated slots
                if (expectedState == null || expectedState.getStatus() == SlotLifecycleState.TERMINATED) {
                    continue;
                }
                // missing slot
                fullSlotStatus = SlotStatus.createSlotStatusWithExpectedState(uuid,
                        null,
                        null,
                        null,
                        "/unknown",
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

    private <T> ImmutableList<T> parallelCommand(Iterable<RemoteSlot> items, final Function<RemoteSlot, T> function)
    {
        ImmutableCollection<Collection<RemoteSlot>> slotsByInstance = Multimaps.index(items, new Function<RemoteSlot, Object>()
        {
            @Override
            public Object apply(RemoteSlot input)
            {
                return input.status().getInstanceId();
            }
        }).asMap().values();

        // run commands for different instances in parallel
        return ImmutableList.copyOf(concat(parallel(slotsByInstance, new Function<Collection<RemoteSlot>, List<T>>()
        {
            public List<T> apply(Collection<RemoteSlot> input)
            {
                // but run commands for a single instance serially
                return ImmutableList.copyOf(transform(input, function));
            }
        })));
    }

    private <F, T> ImmutableList<T> parallel(Iterable<F> items, final Function<F, T> function)
    {
        List<Callable<T>> callables = ImmutableList.copyOf(transform(items, new Function<F, Callable<T>>()
        {
            public Callable<T> apply(@Nullable final F item)
            {
                return new CallableFunction<>(item, function);
            }
        }));

        List<Future<T>> futures;
        try {
            futures = executor.invokeAll(callables);
        }
        catch (InterruptedException e) {
            throw new RuntimeException("Interrupted while waiting for command to finish", e);
        }

        List<Throwable> failures = new ArrayList<>();
        ImmutableList.Builder<T> results = ImmutableList.builder();
        for (Future<T> future : futures) {
            try {
                results.add(future.get());
            }
            catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                failures.add(e);
            }
            catch (CancellationException e) {
                failures.add(e);
            }
            catch (ExecutionException e) {
                if (e.getCause() != null) {
                    failures.add(e.getCause());
                } else {
                    failures.add(e);
                }
            }
        }
        if (!failures.isEmpty()) {
            Throwable first = failures.get(0);
            RuntimeException runtimeException = new RuntimeException(first.getMessage());
            for (Throwable failure : failures) {
                runtimeException.addSuppressed(failure);
            }
            throw runtimeException;
        }
        return results.build();
    }

    private static void waitForFutures(Iterable<ListenableFuture<?>> futures)
    {
        try {
            Futures.allAsList(futures).get();
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        catch (ExecutionException ignored) {
        }
    }

    private static class CallableFunction<F, T>
            implements Callable<T>
    {
        private final F item;
        private final Function<F, T> function;

        private CallableFunction(F item, Function<F, T> function)
        {
            this.item = item;
            this.function = function;
        }

        @Override
        public T call()
        {
            return function.apply(item);
        }

    }
}
