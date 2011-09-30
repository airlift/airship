package com.proofpoint.galaxy.coordinator;

import com.proofpoint.galaxy.shared.AgentStatus;
import com.proofpoint.galaxy.shared.Installation;
import com.proofpoint.galaxy.shared.SlotStatus;

import java.util.List;
import java.util.UUID;

public interface RemoteAgent
{
    SlotStatus install(Installation installation);

    SlotStatus terminateSlot(UUID slotId);

    AgentStatus status();

    List<? extends RemoteSlot> getSlots();

    long getLastUpdateTimestamp();

    void updateStatus(AgentStatus status);

    void markAgentOffline();
}
