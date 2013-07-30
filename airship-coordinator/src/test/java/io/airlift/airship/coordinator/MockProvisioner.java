package io.airlift.airship.coordinator;

import com.google.common.base.Function;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import io.airlift.airship.shared.AgentLifecycleState;
import io.airlift.airship.shared.AgentStatus;
import io.airlift.airship.shared.CoordinatorLifecycleState;
import io.airlift.airship.shared.CoordinatorStatus;
import io.airlift.airship.shared.SlotStatus;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class MockProvisioner implements Provisioner
{
    private final Map<String, CoordinatorStatus> coordinators = new ConcurrentHashMap<>();
    private final Map<String, AgentStatus> agents = new ConcurrentHashMap<>();
    private final RemoteCoordinatorFactory coordinatorFactory = new MockRemoteCoordinatorFactory(coordinators);
    private final RemoteAgentFactory agentFactory = new MockRemoteAgentFactory(agents);
    private final AtomicInteger nextInstanceId = new AtomicInteger();

    public RemoteCoordinatorFactory getCoordinatorFactory()
    {
        return coordinatorFactory;
    }

    public RemoteAgentFactory getAgentFactory()
    {
        return agentFactory;
    }

    public void addCoordinators(CoordinatorStatus... instances)
    {
        addCoordinators(ImmutableList.copyOf(instances));
    }

    public void addCoordinators(Iterable<CoordinatorStatus> coordinatorStatuses)
    {
        for (CoordinatorStatus coordinatorStatus : coordinatorStatuses) {
            coordinators.put(coordinatorStatus.getInstanceId(), coordinatorStatus);
        }
    }

    public void removeCoordinators(String... coordinatorIds)
    {
        removeCoordinators(ImmutableList.copyOf(coordinatorIds));
    }

    public void removeCoordinators(Iterable<String> coordinatorIds)
    {
        for (String coordinatorId : coordinatorIds) {
            coordinators.remove(coordinatorId);
        }
    }

    public void clearCoordinators()
    {
        coordinators.clear();
    }

    @Override
    public List<Instance> listCoordinators()
    {
        return ImmutableList.copyOf(Iterables.transform(coordinators.values(), new Function<CoordinatorStatus, Instance>()
        {
            @Override
            public Instance apply(CoordinatorStatus coordinatorStatus)
            {
                return new Instance(coordinatorStatus.getInstanceId(),
                        coordinatorStatus.getInstanceType(),
                        coordinatorStatus.getLocation(),
                        coordinatorStatus.getInternalUri(),
                        coordinatorStatus.getExternalUri());
            }
        }));
    }

    @Override
    public List<Instance> provisionCoordinators(String coordinatorConfigSpec,
            int coordinatorCount,
            String instanceType,
            String availabilityZone,
            String ami,
            String keyPair,
            String securityGroup,
            String subnetId,
            String privateIpAddress,
            String provisioningScriptsArtifact)
    {
        ImmutableList.Builder<Instance> instances = ImmutableList.builder();
        for (int i = 0; i < coordinatorCount; i++) {
            String coordinatorInstanceId = String.format("i-%05d", nextInstanceId.incrementAndGet());
            String location = String.format("/mock/%s/coordinator", coordinatorInstanceId);

            CoordinatorStatus coordinatorStatus = new CoordinatorStatus(null,
                    CoordinatorLifecycleState.PROVISIONING,
                    coordinatorInstanceId,
                    null,
                    null,
                    location,
                    instanceType);

            instances.add(new Instance(coordinatorStatus.getInstanceId(),
                        coordinatorStatus.getInstanceType(),
                        coordinatorStatus.getLocation(),
                        null,
                        null));

            addCoordinators(coordinatorStatus);
        }

        return instances.build();
    }
    
    public CoordinatorStatus startCoordinator(String instanceId) {
        CoordinatorStatus coordinatorStatus = coordinators.get(instanceId);
        Preconditions.checkNotNull(coordinatorStatus, "coordinatorStatus is null");

        String coordinatorId = UUID.randomUUID().toString();
        URI internalUri = URI.create("fake:/" + coordinatorId + "/internal");
        URI externalUri = URI.create("fake:/" + coordinatorId + "/external");
        CoordinatorStatus newCoordinatorStatus = new CoordinatorStatus(coordinatorId,
                CoordinatorLifecycleState.ONLINE,
                instanceId,
                internalUri,
                externalUri,
                coordinatorStatus.getLocation(),
                coordinatorStatus.getInstanceType());

        coordinators.put(instanceId, newCoordinatorStatus);
        return newCoordinatorStatus;
    }

    public void addAgent(String id, URI agentUri)
    {
        addAgent(id, agentUri, ImmutableMap.<String, Integer>of());
    }

    public void addAgent(String id, URI agentUri, Map<String, Integer> resources)
    {
        String agentInstanceId = String.format("i-%05d", nextInstanceId.incrementAndGet());
        String location = String.format("/mock/%s/agent", agentInstanceId);

        addAgents(new AgentStatus(id,
                agentUri != null ? AgentLifecycleState.ONLINE : AgentLifecycleState.OFFLINE,
                agentInstanceId,
                agentUri,
                agentUri,
                location,
                "unknown",
                ImmutableList.<SlotStatus>of(),
                resources));
    }

    public void addAgents(AgentStatus... agentStatuses)
    {
        addAgents(ImmutableList.copyOf(agentStatuses));
    }

    public void addAgents(Iterable<AgentStatus> agentStatuses)
    {
        for (AgentStatus agentStatus : agentStatuses) {
            agents.put(agentStatus.getInstanceId(), agentStatus);
        }
    }

    public void removeAgents(String... instanceIds)
    {
        removeAgents(ImmutableList.copyOf(instanceIds));
    }

    public void removeAgents(Iterable<String> instanceIds)
    {
        for (String instanceId : instanceIds) {
            agents.remove(instanceId);
        }
    }

    public void clearAgents()
    {
        agents.clear();
    }

    @Override
    public List<Instance> listAgents()
    {
        return ImmutableList.copyOf(Iterables.transform(agents.values(), new Function<AgentStatus, Instance>()
        {
            @Override
            public Instance apply(AgentStatus agentStatus)
            {
                return new Instance(agentStatus.getInstanceId(),
                        agentStatus.getInstanceType(),
                        agentStatus.getLocation(),
                        agentStatus.getInternalUri(),
                        agentStatus.getExternalUri());
            }
        }));
    }

    @Override
    public List<Instance> provisionAgents(String agentConfig,
            int agentCount,
            String instanceType,
            String availabilityZone,
            String ami,
            String keyPair,
            String securityGroup,
            String subnetId,
            String privateIpAddress,
            String provisioningScriptsArtifact)
    {
        if (instanceType == null) {
            instanceType = "default";
        }

        ImmutableList.Builder<Instance> instances = ImmutableList.builder();
        for (int i = 0; i < agentCount; i++) {
            String agentInstanceId = String.format("i-%05d", nextInstanceId.incrementAndGet());
            String location = String.format("/mock/%s/agent", agentInstanceId);

            AgentStatus agentStatus = new AgentStatus(null,
                    AgentLifecycleState.PROVISIONING,
                    agentInstanceId,
                    null,
                    null,
                    location,
                    instanceType,
                    ImmutableList.<SlotStatus>of(),
                    ImmutableMap.<String, Integer>of());

            instances.add(new Instance(agentStatus.getInstanceId(),
                        agentStatus.getInstanceType(),
                        agentStatus.getLocation(),
                        null,
                        null));

            addAgents(agentStatus);
        }

        return instances.build();
    }

    @Override
    public void terminateAgents(Iterable<String> instanceIds)
    {
        removeAgents(instanceIds);
    }

    public AgentStatus startAgent(String instanceId) {
        AgentStatus agentStatus = agents.get(instanceId);
        Preconditions.checkNotNull(agentStatus, "agentStatus is null");

        String agentId = UUID.randomUUID().toString();
        URI internalUri = URI.create("fake:/" + agentId + "/internal");
        URI externalUri = URI.create("fake:/" + agentId + "/external");
        AgentStatus newAgentStatus = new AgentStatus(agentId,
                AgentLifecycleState.ONLINE,
                instanceId,
                internalUri,
                externalUri,
                agentStatus.getLocation(),
                agentStatus.getInstanceType(),
                agentStatus.getSlotStatuses(),
                agentStatus.getResources());

        agents.put(instanceId, newAgentStatus);
        return newAgentStatus;
    }
}
