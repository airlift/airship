package io.airlift.airship.coordinator;

import com.google.common.util.concurrent.ListenableFuture;
import io.airlift.airship.shared.AgentStatus;
import io.airlift.airship.shared.Installation;
import io.airlift.airship.shared.SlotStatus;
import io.airlift.discovery.client.ServiceDescriptor;

import java.net.URI;
import java.util.List;

public interface RemoteAgent
{
    AgentStatus status();

    void setInternalUri(URI uri);

    SlotStatus install(Installation installation);

    List<? extends RemoteSlot> getSlots();

    ListenableFuture<?> updateStatus();

    void setServiceInventory(List<ServiceDescriptor> serviceInventory);
}
