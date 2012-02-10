package com.proofpoint.galaxy.coordinator;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;

import javax.inject.Inject;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class LocalProvisioner implements Provisioner
{
    private final Map<String, Instance> coordinators = new ConcurrentHashMap<String, Instance>();
    private final Map<String, Instance> agents = new ConcurrentHashMap<String, Instance>();

    public LocalProvisioner()
    {
    }

    @Inject
    public LocalProvisioner(LocalProvisionerConfig config)
    {
        this(config.getLocalAgentUris());
    }

    public LocalProvisioner(List<String> localAgentUris)
    {
        Preconditions.checkNotNull(localAgentUris, "localAgentUri is null");
        for (String localAgentUri : localAgentUris) {
            addAgent(new Instance("local", "unknown", "location", URI.create(localAgentUri)));
        }
        addAgent(new Instance("bad", "unknown", "location", null));
    }

    public void addCoordinator(Instance instance) {
        coordinators.put(instance.getInstanceId(), instance);
    }

    public void removeCoordinator(String coordinatorId) {
        coordinators.remove(coordinatorId);
    }

    @Override
    public List<Instance> listCoordinators()
    {
        return ImmutableList.copyOf(coordinators.values());
    }

    public void addAgent(Instance instance)
    {
        agents.put(instance.getInstanceId(), instance);
    }

    public void removeAgent(String agentId)
    {
        agents.remove(agentId);
    }

    @Override
    public List<Instance> listAgents()
    {
        return ImmutableList.copyOf(agents.values());
    }

    @Override
    public List<Instance> provisionAgents(String agentConfig, int agentCount, String instanceType, String availabilityZone)
            throws Exception
    {
        throw new UnsupportedOperationException("Agent provisioning not supported in local mode");
    }

    @Override
    public void terminateAgents(List<String> instanceIds)
    {
        throw new UnsupportedOperationException("Agent termination not supported in local mode");
    }
}
