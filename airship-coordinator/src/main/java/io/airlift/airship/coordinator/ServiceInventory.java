package io.airlift.airship.coordinator;

import io.airlift.discovery.client.ServiceDescriptor;
import io.airlift.airship.shared.SlotStatus;

import java.util.List;

public interface ServiceInventory
{
    List<ServiceDescriptor> getServiceInventory(Iterable<SlotStatus> allSlotStatus);
}
