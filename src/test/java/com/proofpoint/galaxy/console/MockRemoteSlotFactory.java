package com.proofpoint.galaxy.console;

import com.proofpoint.galaxy.SlotStatus;

public class MockRemoteSlotFactory implements RemoteSlotFactory
{
    @Override
    public RemoteSlot createRemoteSlot(SlotStatus slotStatus)
    {
        return new MockRemoteSlot(slotStatus);
    }
}
