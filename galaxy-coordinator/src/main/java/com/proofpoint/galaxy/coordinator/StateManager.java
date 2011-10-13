package com.proofpoint.galaxy.coordinator;

import java.util.Collection;

public interface StateManager
{
    Collection<ExpectedSlotStatus> getAllExpectedStates();

    void setExpectedState(ExpectedSlotStatus slotStatus);
}
