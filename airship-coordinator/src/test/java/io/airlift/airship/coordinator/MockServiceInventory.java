package com.proofpoint.galaxy.coordinator;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.proofpoint.discovery.client.ServiceDescriptor;
import com.proofpoint.galaxy.shared.SlotStatus;

import java.util.List;

public class MockServiceInventory implements ServiceInventory
{
    private ImmutableList<ServiceDescriptor> serviceInventory = ImmutableList.of();

    public MockServiceInventory()
    {
        serviceInventory = ImmutableList.of();
    }

    @Override
    public List<ServiceDescriptor> getServiceInventory(Iterable<SlotStatus> allSlotStatus)
    {
        return serviceInventory;
    }

    public void setServiceInventory(Iterable<ServiceDescriptor> serviceInventory)
    {
        Preconditions.checkNotNull(serviceInventory, "serviceInventory is null");
        this.serviceInventory = ImmutableList.copyOf(serviceInventory);
    }
}
