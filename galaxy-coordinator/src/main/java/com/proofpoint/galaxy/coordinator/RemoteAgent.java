package com.proofpoint.galaxy.coordinator;

import com.proofpoint.discovery.client.ServiceDescriptor;
import com.proofpoint.galaxy.shared.AgentStatus;
import com.proofpoint.galaxy.shared.Installation;
import com.proofpoint.galaxy.shared.SlotStatus;

import java.net.URI;
import java.util.List;
import java.util.UUID;

public interface RemoteAgent
{
    URI getUri();

    void setUri(URI uri);

    SlotStatus install(Installation installation);

    SlotStatus terminateSlot(UUID slotId);

    AgentStatus status();

    List<? extends RemoteSlot> getSlots();

    void updateStatus();

    void setStatus(AgentStatus status);

    void setServiceInventory(List<ServiceDescriptor> serviceInventory);
}
