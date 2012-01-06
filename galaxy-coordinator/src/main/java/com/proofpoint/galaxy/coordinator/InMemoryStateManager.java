package com.proofpoint.galaxy.coordinator;

import com.google.common.collect.ImmutableList;

import java.util.Collection;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class InMemoryStateManager implements StateManager
{
    private final ConcurrentMap<String, ExpectedSlotStatus> expectedState = new ConcurrentHashMap<String, ExpectedSlotStatus>();

    public void clearAll()
    {
        expectedState.clear();
    }

    @Override
    public Collection<ExpectedSlotStatus> getAllExpectedStates()
    {
        return ImmutableList.copyOf(expectedState.values());
    }

    @Override
    public void deleteExpectedState(UUID slotId)
    {
        expectedState.remove(slotId.toString());
    }

    @Override
    public void setExpectedState(ExpectedSlotStatus slotStatus)
    {
        expectedState.put(slotStatus.getId().toString(), slotStatus);
    }
}
