package com.proofpoint.galaxy.coordinator;

import com.google.common.base.Function;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.proofpoint.discovery.client.ServiceDescriptor;
import com.proofpoint.galaxy.shared.AgentLifecycleState;
import com.proofpoint.galaxy.shared.AgentStatus;
import com.proofpoint.galaxy.shared.Installation;
import com.proofpoint.galaxy.shared.SlotLifecycleState;
import com.proofpoint.galaxy.shared.SlotStatus;
import org.codehaus.jackson.annotate.JsonTypeInfo.As;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.proofpoint.galaxy.shared.AgentLifecycleState.*;
import static com.proofpoint.galaxy.shared.SlotStatus.createSlotStatus;

public class MockRemoteAgent implements RemoteAgent
{
    private final String instanceId;
    private final Map<String, AgentStatus> agents;

    public MockRemoteAgent(String instanceId, Map<String, AgentStatus> agents)
    {
        this.instanceId = instanceId;
        this.agents = agents;
    }

    @Override
    public AgentStatus status()
    {
        return getAgentStatus();
    }

    @Override
    public void setInternalUri(URI internalUri)
    {
        setAgentStatus(getAgentStatus().changeInternalUri(internalUri));
    }

    @Override
    public List<? extends RemoteSlot> getSlots()
    {
        return ImmutableList.copyOf(Iterables.transform(getAgentStatus().getSlotStatuses(), new Function<SlotStatus, MockRemoteSlot>()
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
        AgentStatus agentStatus = getAgentStatus().changeSlotStatus(slotStatus);
        setAgentStatus(agentStatus);
    }

    @Override
    public void setServiceInventory(List<ServiceDescriptor> serviceInventory)
    {
    }

    @Override
    public SlotStatus install(Installation installation)
    {
        checkNotNull(installation, "installation is null");

        AgentStatus agentStatus = getAgentStatus();
        Preconditions.checkState(agentStatus.getState() != OFFLINE, "agent is offline");

        UUID slotId = UUID.randomUUID();
        SlotStatus slotStatus = createSlotStatus(slotId,
                "",
                agentStatus.getInternalUri().resolve("slot/" + slotId),
                agentStatus.getExternalUri().resolve("slot/" + slotId),
                "instance",
                "/location",
                SlotLifecycleState.STOPPED,
                installation.getAssignment(),
                "/" + slotId,
                installation.getResources());
        agentStatus.changeSlotStatus(slotStatus);

        return slotStatus;
    }

    public AgentStatus getAgentStatus()
    {
        AgentStatus agentStatus = agents.get(instanceId);
        if (agentStatus != null) {
            return agentStatus;
        } else {
            return new AgentStatus(null,
                    AgentLifecycleState.OFFLINE,
                    instanceId,
                    null,
                    null,
                    null,
                    null,
                    ImmutableList.<SlotStatus>of(),
                    ImmutableMap.<String, Integer>of());
        }
    }

    public void setAgentStatus(AgentStatus agentStatus)
    {
        Preconditions.checkNotNull(agentStatus, "agentStatus is null");
        agents.put(instanceId, agentStatus);
    }
}
