package com.proofpoint.galaxy.standalone;

import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.proofpoint.discovery.client.ServiceDescriptor;
import com.proofpoint.galaxy.agent.Agent;
import com.proofpoint.galaxy.agent.DeploymentManagerFactory;
import com.proofpoint.galaxy.agent.DirectoryDeploymentManagerFactory;
import com.proofpoint.galaxy.agent.LauncherLifecycleManager;
import com.proofpoint.galaxy.agent.LifecycleManager;
import com.proofpoint.galaxy.agent.Slot;
import com.proofpoint.galaxy.coordinator.Coordinator;
import com.proofpoint.galaxy.coordinator.CoordinatorConfig;
import com.proofpoint.galaxy.coordinator.HttpRepository;
import com.proofpoint.galaxy.coordinator.HttpServiceInventory;
import com.proofpoint.galaxy.coordinator.InMemoryStateManager;
import com.proofpoint.galaxy.coordinator.Instance;
import com.proofpoint.galaxy.coordinator.MavenRepository;
import com.proofpoint.galaxy.coordinator.Provisioner;
import com.proofpoint.galaxy.coordinator.RemoteAgent;
import com.proofpoint.galaxy.coordinator.RemoteAgentFactory;
import com.proofpoint.galaxy.coordinator.RemoteSlot;
import com.proofpoint.galaxy.coordinator.ServiceInventory;
import com.proofpoint.galaxy.coordinator.StateManager;
import com.proofpoint.galaxy.shared.AgentStatus;
import com.proofpoint.galaxy.shared.ExpectedSlotStatus;
import com.proofpoint.galaxy.shared.Installation;
import com.proofpoint.galaxy.shared.Repository;
import com.proofpoint.galaxy.shared.RepositorySet;
import com.proofpoint.galaxy.shared.SlotStatus;
import com.proofpoint.json.JsonCodec;
import com.proofpoint.units.Duration;

import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static com.google.common.collect.Lists.newArrayList;

public class CommanderFactory
{
    private static final String AGENT_ID = "standalone";
    private static final String INSTANCE_TYPE = "standalone";
    private static final String LOCATION = Joiner.on('/').join("localhost", AGENT_ID, "agent");
    private static final URI AGENT_URI = URI.create("java://localhost");
    private static final Duration COMMAND_TIMEOUT = new Duration(5, TimeUnit.MINUTES);

    private String environment;
    private URI coordinatorUri;
    private final List<String> repositories = newArrayList();
    private final List<String> mavenDefaultGroupIds = newArrayList();

    public CommanderFactory setEnvironment(String environment)
    {
        this.environment = environment;
        return this;
    }

    public CommanderFactory setCoordinatorUri(URI coordinatorUri)
    {
        this.coordinatorUri = coordinatorUri;
        return this;
    }

    public CommanderFactory setRepositories(List<String> repositories)
    {
        this.repositories.clear();
        this.repositories.addAll(repositories);
        return this;
    }

    public CommanderFactory setMavenDefaultGroupIds(List<String> mavenDefaultGroupIds)
    {
        this.mavenDefaultGroupIds.clear();
        this.mavenDefaultGroupIds.addAll(mavenDefaultGroupIds);
        return this;
    }

    public Commander build()
            throws IOException
    {
        Preconditions.checkNotNull(coordinatorUri, "coordinatorUri is null");

        String scheme = coordinatorUri.getScheme();
        if ("http".equals(scheme)) {
            return new HttpCommander(coordinatorUri);
        }
        else if ("file".equals(scheme) || scheme == null) {
            Coordinator coordinator = createLocalCoordinator();

            return new LocalCommander(coordinator);
        }
        throw new IllegalAccessError("Unsupported coordinator protocol " + scheme);
    }

    private Coordinator createLocalCoordinator()
    {
        Preconditions.checkNotNull(coordinatorUri, "coordinatorUri is null");
        Preconditions.checkNotNull(environment, "environment is null");
        Preconditions.checkNotNull(this.repositories, "binaryRepositories is null");

        List<URI> repoBases = ImmutableList.copyOf(Lists.transform(repositories, new ToUriFunction()));

        //
        // Create agent
        //
        String slotsDir = coordinatorUri.getPath();
        DeploymentManagerFactory deploymentManagerFactory = new DirectoryDeploymentManagerFactory(LOCATION, slotsDir, COMMAND_TIMEOUT);

        LifecycleManager lifecycleManager = new LauncherLifecycleManager(
                environment,
                AGENT_URI,
                null,
                COMMAND_TIMEOUT,
                COMMAND_TIMEOUT);

        Agent agent = new Agent(AGENT_ID,
                LOCATION,
                slotsDir,
                AGENT_URI,
                null,
                deploymentManagerFactory,
                lifecycleManager,
                COMMAND_TIMEOUT);

        //
        // Create coordinator
        //
        CoordinatorConfig coordinatorConfig = new CoordinatorConfig()
                .setRepositories(repositories)
                .setDefaultRepositoryGroupId(mavenDefaultGroupIds);
        Repository repository = new RepositorySet(ImmutableSet.<Repository>of(
                new MavenRepository(coordinatorConfig),
                new HttpRepository(coordinatorConfig)));
        ServiceInventory serviceInventory = new HttpServiceInventory(repository, JsonCodec.listJsonCodec(ServiceDescriptor.class));

        Provisioner provisioner = new StandaloneProvisioner();

        StateManager stateManager = new InMemoryStateManager();
        for (SlotStatus slotStatus : agent.getAgentStatus().getSlotStatuses()) {
            stateManager.setExpectedState(new ExpectedSlotStatus(slotStatus.getId(), slotStatus.getState(), slotStatus.getAssignment()));
        }

        RemoteAgentFactory remoteAgentFactory = new StandaloneRemoteAgentFactory(agent);

        return new Coordinator(environment,
                remoteAgentFactory,
                repository,
                provisioner,
                stateManager,
                serviceInventory,
                new Duration(100, TimeUnit.DAYS));
    }

    private static class StandaloneProvisioner implements Provisioner
    {
        @Override
        public List<Instance> listAgents()
        {
            return ImmutableList.of(new Instance(AGENT_ID, INSTANCE_TYPE, LOCATION, AGENT_URI));
        }

        @Override
        public List<Instance> provisionAgents(String agentConfig, int agentCount, String instanceType, String availabilityZone)
                throws Exception
        {
            throw new UnsupportedOperationException("Agents can not be provisioned in Galaxy standalone");
        }

        @Override
        public void terminateAgents(List<String> instanceIds)
        {
            throw new UnsupportedOperationException("Agents can not be terminated in Galaxy standalone");
        }
    }

    private static class StandaloneRemoteAgentFactory implements RemoteAgentFactory
    {
        private final Agent agent;

        public StandaloneRemoteAgentFactory(Agent agent)
        {
            this.agent = agent;
        }

        @Override
        public RemoteAgent createRemoteAgent(String instanceId, String instanceType, URI uri)
        {
            Preconditions.checkArgument(AGENT_ID.equals(instanceId), "instanceId is not '" + AGENT_ID + "'");
            Preconditions.checkArgument(AGENT_URI.equals(uri), "uri is not '" + AGENT_URI + "'");

            return new StandaloneRemoteAgent(agent);
        }
    }

    private static class StandaloneRemoteAgent implements RemoteAgent
    {
        private final Agent agent;

        public StandaloneRemoteAgent(Agent agent)
        {
            this.agent = agent;
        }

        @Override
        public URI getUri()
        {
            return AGENT_URI;
        }

        @Override
        public void setUri(URI uri)
        {
            Preconditions.checkArgument(AGENT_URI.equals(uri), "uri is not '" + AGENT_URI + "'");
        }

        @Override
        public SlotStatus install(Installation installation)
        {
            return agent.install(installation);
        }

        @Override
        public AgentStatus status()
        {
            AgentStatus agentStatus = agent.getAgentStatus();
            return new AgentStatus(agentStatus.getAgentId(),
                    agentStatus.getState(),
                    agentStatus.getUri(),
                    agentStatus.getLocation(),
                    INSTANCE_TYPE,
                    agentStatus.getSlotStatuses(),
                    agentStatus.getResources());
        }

        @Override
        public List<? extends RemoteSlot> getSlots()
        {
            ImmutableList.Builder<RemoteSlot> builder = ImmutableList.builder();
            for (Slot slot : agent.getAllSlots()) {
                builder.add(new StandaloneRemoteSlot(slot));
            }
            return builder.build();
        }

        @Override
        public void updateStatus()
        {
        }

        @Override
        public void setServiceInventory(List<ServiceDescriptor> serviceInventory)
        {
        }

        @Override
        public void setStatus(AgentStatus status)
        {
            // only used for testing
        }
    }

    private static class StandaloneRemoteSlot implements RemoteSlot
    {
        private final Slot slot;

        public StandaloneRemoteSlot(Slot slot)
        {
            this.slot = slot;
        }

        @Override
        public UUID getId()
        {
            return slot.getId();
        }

        @Override
        public SlotStatus terminate()
        {
            return slot.terminate();
        }

        @Override
        public SlotStatus assign(Installation installation)
        {
            return slot.assign(installation);
        }

        @Override
        public SlotStatus status()
        {
            return slot.status();
        }

        @Override
        public SlotStatus start()
        {
            return slot.start();
        }

        @Override
        public SlotStatus restart()
        {
            return slot.restart();
        }

        @Override
        public SlotStatus stop()
        {
            return slot.stop();
        }
    }

    public static class ToUriFunction implements Function<String, URI>
    {
        public URI apply(String uri)
        {
            if (!uri.endsWith("/")) {
                uri = uri + "/";
            }
            return URI.create(uri);
        }
    }
}
