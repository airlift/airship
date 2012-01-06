package com.proofpoint.galaxy.coordinator;

import java.util.Collection;
import java.util.UUID;

public interface StateManager
{
    Collection<ExpectedSlotStatus> getAllExpectedStates();

    void deleteExpectedState(UUID slotId);

    void setExpectedState(ExpectedSlotStatus slotStatus);
}
