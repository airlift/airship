package com.proofpoint.galaxy;

public interface RemoteSlotFactory
{
    RemoteSlot createRemoteSlot(SlotStatus slotStatus);
}
