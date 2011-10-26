package com.proofpoint.galaxy.coordinator;

import com.google.common.base.Preconditions;
import com.proofpoint.galaxy.shared.SlotStatus;
import com.proofpoint.galaxy.shared.Installation;

import java.util.UUID;

import static com.proofpoint.galaxy.shared.SlotLifecycleState.RUNNING;
import static com.proofpoint.galaxy.shared.SlotLifecycleState.STOPPED;
import static com.proofpoint.galaxy.shared.SlotLifecycleState.TERMINATED;

public class MockRemoteSlot implements RemoteSlot
{
    private SlotStatus slotStatus;
    private final MockRemoteAgent mockRemoteAgent;

    public MockRemoteSlot(SlotStatus slotStatus, MockRemoteAgent mockRemoteAgent)
    {
        this.slotStatus = slotStatus;
        this.mockRemoteAgent = mockRemoteAgent;
    }

    @Override
    public UUID getId()
    {
        return slotStatus.getId();
    }


    @Override
    public SlotStatus status()
    {
        return slotStatus;
    }

    @Override
    public SlotStatus assign(Installation installation)
    {
        Preconditions.checkNotNull(installation, "installation is null");
        slotStatus = new SlotStatus(slotStatus, STOPPED, installation.getAssignment());
        mockRemoteAgent.setSlotStatus(slotStatus);
        return slotStatus;
    }

    @Override
    public SlotStatus terminate()
    {
        if (slotStatus.getState() == STOPPED) {
            slotStatus = slotStatus.updateState(TERMINATED);
        }
        mockRemoteAgent.setSlotStatus(slotStatus);
        return slotStatus;
    }

    @Override
    public SlotStatus start()
    {
        if (slotStatus.getAssignment() == null) {
            throw new IllegalStateException("Slot can not be started because the slot is not assigned");
        }
        slotStatus = slotStatus.updateState(RUNNING);
        mockRemoteAgent.setSlotStatus(slotStatus);
        return slotStatus;
    }

    @Override
    public SlotStatus restart()
    {
        if (slotStatus.getAssignment() == null) {
            throw new IllegalStateException("Slot can not be restarted because the slot is not assigned");
        }
        slotStatus = slotStatus.updateState(RUNNING);
        mockRemoteAgent.setSlotStatus(slotStatus);
        return slotStatus;
    }

    @Override
    public SlotStatus stop()
    {
        if (slotStatus.getAssignment() == null) {
            throw new IllegalStateException("Slot can not be stopped because the slot is not assigned");
        }
        slotStatus = slotStatus.updateState(STOPPED);
        mockRemoteAgent.setSlotStatus(slotStatus);
        return slotStatus;
    }
}
