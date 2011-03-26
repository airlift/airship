package com.proofpoint.galaxy.console;

import com.proofpoint.galaxy.SlotStatus;

public interface RemoteSlotFactory
{
    RemoteSlot createRemoteSlot(SlotStatus slotStatus);
}
