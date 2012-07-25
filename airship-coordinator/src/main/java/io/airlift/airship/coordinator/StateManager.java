package io.airlift.airship.coordinator;

import io.airlift.airship.shared.ExpectedSlotStatus;

import java.util.Collection;
import java.util.UUID;

public interface StateManager
{
    Collection<ExpectedSlotStatus> getAllExpectedStates();

    void deleteExpectedState(UUID slotId);

    void setExpectedState(ExpectedSlotStatus slotStatus);
}
