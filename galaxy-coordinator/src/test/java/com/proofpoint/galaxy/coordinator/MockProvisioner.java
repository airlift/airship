package com.proofpoint.galaxy.coordinator;

import com.google.common.base.Function;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.proofpoint.galaxy.shared.AgentStatus;
import com.proofpoint.galaxy.shared.SlotStatus;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static com.proofpoint.galaxy.shared.AgentLifecycleState.ONLINE;

public class MockProvisioner implements Provisioner
{
    private final Map<String, Instance> coordinators = new ConcurrentHashMap<String, Instance>();
    private final Map<String, AgentStatus> agents = new ConcurrentHashMap<String, AgentStatus>();
    private final RemoteAgentFactory agentFactory = new MockRemoteAgentFactory(agents);

    public RemoteAgentFactory getAgentFactory()
    {
        return agentFactory;
    }

    public void addCoordinator(Instance instance)
    {
        coordinators.put(instance.getInstanceId(), instance);
    }

    public void removeCoordinator(String coordinatorId)
    {
        coordinators.remove(coordinatorId);
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
        throw new UnsupportedOperationException("Coordinators can not be provisioned in local mode");
    }

    public void addAgent(String id, URI agentUri)
    {
        addAgent(id, agentUri, ImmutableMap.<String, Integer>of());
    }

    public void addAgent(String id, URI agentUri, Map<String, Integer> resources)
    {
        addAgent(new AgentStatus(id,
                ONLINE,
                id,
                agentUri,
                agentUri,
                "location",
                "test.type",
                ImmutableList.<SlotStatus>of(),
                resources));
    }

    public void addAgent(AgentStatus agentStatus)
    {
        agents.put(agentStatus.getInstanceId(), agentStatus);
    }

    public void removeAgent(String instanceId)
    {
        agents.remove(instanceId);
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
                        "unknown",
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
        throw new UnsupportedOperationException("Agents can not be provisioned in local mode");
    }

    @Override
    public void terminateAgents(List<String> instanceIds)
    {
        throw new UnsupportedOperationException("Agents can not be termination in local mode");
    }


}
