package com.proofpoint.galaxy;

public class MockRemoteSlotFactory implements RemoteSlotFactory
{
    @Override
    public RemoteSlot createRemoteSlot(SlotStatus slotStatus)
    {
        return new MockRemoteSlot(slotStatus);
    }
}
