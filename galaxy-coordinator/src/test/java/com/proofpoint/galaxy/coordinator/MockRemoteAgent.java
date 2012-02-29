package com.proofpoint.galaxy.coordinator;

import com.google.common.base.Function;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.proofpoint.discovery.client.ServiceDescriptor;
import com.proofpoint.galaxy.shared.AgentStatus;
import com.proofpoint.galaxy.shared.Installation;
import com.proofpoint.galaxy.shared.SlotLifecycleState;
import com.proofpoint.galaxy.shared.SlotStatus;

import java.net.URI;
import java.util.List;
import java.util.UUID;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.proofpoint.galaxy.shared.AgentLifecycleState.*;
import static com.proofpoint.galaxy.shared.SlotStatus.createSlotStatus;

public class MockRemoteAgent implements RemoteAgent
{
    private AgentStatus agentStatus;

    public MockRemoteAgent(AgentStatus agentStatus)
    {
        this.agentStatus = checkNotNull(agentStatus, "agentStatus is null");
    }

    @Override
    public AgentStatus status()
    {
        return agentStatus;
    }

    @Override
    public void setInternalUri(URI internalUri)
    {
        agentStatus = agentStatus.changeInternalUri(internalUri);
    }

    @Override
    public List<? extends RemoteSlot> getSlots()
    {
        return ImmutableList.copyOf(Iterables.transform(agentStatus.getSlotStatuses(), new Function<SlotStatus, MockRemoteSlot>()
        {
            @Override
            public MockRemoteSlot apply(SlotStatus slotStatus)
            {
                return new MockRemoteSlot(slotStatus, MockRemoteAgent.this);
            }
        }));
    }

    @Override
    public void updateStatus()
    {
    }

    void setSlotStatus(SlotStatus slotStatus)
    {
        agentStatus = agentStatus.changeSlotStatus(slotStatus);
    }

    @Override
    public void setServiceInventory(List<ServiceDescriptor> serviceInventory)
    {
    }

    @Override
    public SlotStatus install(Installation installation)
    {
        Preconditions.checkNotNull(installation, "installation is null");
        Preconditions.checkState(agentStatus.getState() != OFFLINE, "agent is offline");

        UUID slotId = UUID.randomUUID();
        SlotStatus slotStatus = createSlotStatus(slotId,
                "",
                agentStatus.getInternalUri().resolve("slot/" + slotId),
                agentStatus.getExternalUri().resolve("slot/" + slotId),
                "location",
                SlotLifecycleState.STOPPED,
                installation.getAssignment(),
                "/" + slotId,
                installation.getResources());
        agentStatus.changeSlotStatus(slotStatus);

        return slotStatus;
    }
}
