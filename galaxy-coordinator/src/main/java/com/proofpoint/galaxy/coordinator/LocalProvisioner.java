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
    private final Map<String, Instance> instances = new ConcurrentHashMap<String, Instance>();

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
            addAgent(new Instance("local", "unknown", "loation", URI.create(localAgentUri)));
        }
        addAgent(new Instance("bad", "unknown", "location", null));

    }

    public void addAgent(Instance location)
    {
        instances.put(location.getInstanceId(), location);
    }

    public void removeAgent(String agentId)
    {
        instances.remove(agentId);
    }

    @Override
    public List<Instance> listAgents()
    {
        return ImmutableList.copyOf(instances.values());
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
