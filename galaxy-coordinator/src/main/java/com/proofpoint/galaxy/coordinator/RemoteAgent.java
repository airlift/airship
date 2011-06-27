package com.proofpoint.galaxy.coordinator;

import com.proofpoint.galaxy.shared.AgentStatus;
import com.proofpoint.galaxy.shared.Installation;
import com.proofpoint.galaxy.shared.SlotStatus;

import java.util.List;

public interface RemoteAgent
{
    SlotStatus install(Installation installation);

    AgentStatus status();

    List<? extends RemoteSlot> getSlots();

    void updateStatus(AgentStatus status);

    void agentOffline();
}