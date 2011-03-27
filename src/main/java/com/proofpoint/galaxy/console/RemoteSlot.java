package com.proofpoint.galaxy.console;

import com.proofpoint.galaxy.SlotStatus;
import com.proofpoint.galaxy.agent.Assignment;

import java.net.URI;
import java.util.UUID;

public interface RemoteSlot
{
    UUID getId();

    SlotStatus assign(Assignment assignment);

    SlotStatus clear();

    void updateStatus(SlotStatus slotStatus);

    SlotStatus status();

    SlotStatus start();

    SlotStatus restart();

    SlotStatus stop();

}
