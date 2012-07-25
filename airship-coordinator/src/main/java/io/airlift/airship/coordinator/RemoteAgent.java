package io.airlift.airship.coordinator;

import com.proofpoint.discovery.client.ServiceDescriptor;
import io.airlift.airship.shared.AgentStatus;
import io.airlift.airship.shared.Installation;
import io.airlift.airship.shared.SlotStatus;

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
