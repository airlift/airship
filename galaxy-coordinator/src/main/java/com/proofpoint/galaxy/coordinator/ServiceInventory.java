package com.proofpoint.galaxy.coordinator;

import com.proofpoint.discovery.client.ServiceDescriptor;
import com.proofpoint.galaxy.shared.SlotStatus;

import java.util.List;

public interface ServiceInventory
{
    List<ServiceDescriptor> getServiceInventory(List<SlotStatus> allSlotStatus);
}
