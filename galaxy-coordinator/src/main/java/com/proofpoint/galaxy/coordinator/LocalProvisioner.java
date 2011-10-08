package com.proofpoint.galaxy.coordinator;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;

import javax.inject.Inject;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class LocalProvisioner implements Provisioner
{
    private final Map<String, Ec2Location> instances = new ConcurrentHashMap<String, Ec2Location>();

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
            addAgent(new Ec2Location("region", "zone", "local", "unknown", URI.create(localAgentUri)));
        }
        addAgent(new Ec2Location("region", "zone", "bad", "unknown"));

    }

    public void addAgent(Ec2Location location)
    {
        instances.put(location.getInstanceId(), location);
    }

    public void removeAgent(String agentId)
    {
        instances.remove(agentId);
    }

    @Override
    public List<Ec2Location> listAgents()
    {
        return ImmutableList.copyOf(instances.values());
    }

    @Override
    public List<Ec2Location> provisionAgents(int agentCount, String instanceType, String availabilityZone)
            throws Exception
    {
        throw new UnsupportedOperationException("Agent provisioning not supported in local mode");
    }
}
