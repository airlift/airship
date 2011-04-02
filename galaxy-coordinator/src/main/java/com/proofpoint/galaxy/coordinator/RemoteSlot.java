package com.proofpoint.galaxy.coordinator;

import com.proofpoint.galaxy.shared.SlotStatus;
import com.proofpoint.galaxy.shared.Installation;

import java.util.UUID;

public interface RemoteSlot
{
    UUID getId();

    SlotStatus assign(Installation installation);

    SlotStatus clear();

    void updateStatus(SlotStatus slotStatus);

    SlotStatus status();

    SlotStatus start();

    SlotStatus restart();

    SlotStatus stop();

}
