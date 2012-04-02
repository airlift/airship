package com.proofpoint.galaxy.coordinator;

import com.google.common.base.Function;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.proofpoint.galaxy.shared.AgentLifecycleState;
import com.proofpoint.galaxy.shared.AgentStatus;
import com.proofpoint.galaxy.shared.SlotStatus;
import com.proofpoint.node.NodeInfo;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class MockProvisioner implements Provisioner
{
    private final Map<String, Instance> coordinators = new ConcurrentHashMap<String, Instance>();
    private final Map<String, AgentStatus> agents = new ConcurrentHashMap<String, AgentStatus>();
    private final RemoteAgentFactory agentFactory = new MockRemoteAgentFactory(agents);
    private final AtomicInteger nextInstanceId = new AtomicInteger();

    public RemoteAgentFactory getAgentFactory()
    {
        return agentFactory;
    }

    public void addCoordinators(Instance... instances)
    {
        addCoordinators(ImmutableList.copyOf(instances));
    }

    public void addCoordinators(Iterable<Instance> instances)
    {
        for (Instance instance : instances) {
            coordinators.put(instance.getInstanceId(), instance);
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
        return ImmutableList.copyOf(coordinators.values());
    }

    @Override
    public List<Instance> provisionCoordinators(String coordinatorConfigSpec,
            int coordinatorCount,
            String instanceType,
            String availabilityZone,
            String ami,
            String keyPair,
            String securityGroup)
    {
        ImmutableList.Builder<Instance> provisionedCoordinators = ImmutableList.builder();
        for (int i = 0; i < coordinatorCount; i++) {
            String coordinatorInstanceId = String.format("i-%05d", nextInstanceId.incrementAndGet());
            String location = String.format("/mock/%s/coordinator", coordinatorInstanceId);
            Instance instance = new Instance(coordinatorInstanceId, instanceType, location, null, null);
            provisionedCoordinators.add(instance);
        }
        addCoordinators(provisionedCoordinators.build());
        return provisionedCoordinators.build();
    }

    public Instance startCoordinator(String instanceId) {
        Instance instance = coordinators.get(instanceId);
        Preconditions.checkNotNull(instance, "instance is null");

        URI internalUri = URI.create("fake:/" + instanceId + "/internal");
        URI externalUri = URI.create("fake:/" + instanceId + "/external");
        Instance newCoordinatorInstance = new Instance(
                instanceId,
                instance.getInstanceType(),
                instance.getLocation(),
                internalUri,
                externalUri);

        coordinators.put(instanceId, newCoordinatorInstance);
        return newCoordinatorInstance;
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
            String securityGroup)
    {
        ImmutableList.Builder<Instance> instances = ImmutableList.builder();
        for (int i = 0; i < agentCount; i++) {
            String agentInstanceId = String.format("i-%05d", nextInstanceId.incrementAndGet());
            String location = String.format("/mock/%s/agent", agentInstanceId);

            AgentStatus agentStatus = new AgentStatus(null,
                    AgentLifecycleState.OFFLINE,
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
