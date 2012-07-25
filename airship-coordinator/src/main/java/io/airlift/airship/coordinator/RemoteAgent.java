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
    AgentStatus status();

    void setInternalUri(URI uri);

    SlotStatus install(Installation installation);

    List<? extends RemoteSlot> getSlots();

    void updateStatus();

    void setServiceInventory(List<ServiceDescriptor> serviceInventory);
}
