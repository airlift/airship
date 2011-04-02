package com.proofpoint.galaxy.coordinator;

import com.proofpoint.galaxy.shared.SlotStatus;

public interface RemoteSlotFactory
{
    RemoteSlot createRemoteSlot(SlotStatus slotStatus);
}
