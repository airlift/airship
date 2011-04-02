package com.proofpoint.galaxy.coordinator;

import com.proofpoint.galaxy.shared.SlotStatus;

public class MockRemoteSlotFactory implements RemoteSlotFactory
{
    @Override
    public RemoteSlot createRemoteSlot(SlotStatus slotStatus)
    {
        return new MockRemoteSlot(slotStatus);
    }
}
