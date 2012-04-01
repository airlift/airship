package com.proofpoint.galaxy.coordinator;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.proofpoint.discovery.client.ServiceDescriptor;
import com.proofpoint.galaxy.shared.AgentLifecycleState;
import com.proofpoint.galaxy.shared.AgentStatus;
import com.proofpoint.galaxy.shared.Installation;
import com.proofpoint.galaxy.shared.SlotStatus;

import java.net.URI;
import java.util.List;

public class ProvisioningRemoteAgent implements RemoteAgent
{
    private final AgentStatus agentStatus;

    public ProvisioningRemoteAgent(Instance instance)
    {
        Preconditions.checkNotNull(instance, "instance is null");
        agentStatus = new AgentStatus(
                null,
                AgentLifecycleState.PROVISIONING,
                instance.getInstanceId(),
                null,
                null,
                instance.getLocation(),
                instance.getInstanceType(),
                ImmutableList.<SlotStatus>of(),
                ImmutableMap.<String, Integer>of());
    }

    @Override
    public AgentStatus status()
    {
        return agentStatus;
    }

    @Override
    public void setInternalUri(URI uri)
    {
    }

    @Override
    public SlotStatus install(Installation installation)
    {
        throw new UnsupportedOperationException("Agent is still provisioning");
    }

    @Override
    public List<? extends RemoteSlot> getSlots()
    {
        return ImmutableList.of();
    }

    @Override
    public void updateStatus()
    {
    }

    @Override
    public void setServiceInventory(List<ServiceDescriptor> serviceInventory)
    {
    }
}
