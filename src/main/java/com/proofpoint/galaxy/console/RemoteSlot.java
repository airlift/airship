package com.proofpoint.galaxy.console;

import com.proofpoint.galaxy.Slot;
import com.proofpoint.galaxy.SlotStatus;

public interface RemoteSlot extends Slot
{
    void setStatus(SlotStatus slotStatus);
}
