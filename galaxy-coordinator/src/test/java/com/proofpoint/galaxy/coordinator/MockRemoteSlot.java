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

    public MockRemoteSlot(SlotStatus slotStatus)
    {
        this.slotStatus = slotStatus;
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
    public void updateStatus(SlotStatus slotStatus)
    {
        Preconditions.checkNotNull(slotStatus, "slotStatus is null");
        Preconditions.checkArgument(slotStatus.getId().equals(this.slotStatus.getId()));
        this.slotStatus = slotStatus;
    }

    @Override
    public SlotStatus assign(Installation installation)
    {
        Preconditions.checkNotNull(installation, "installation is null");
        slotStatus = new SlotStatus(slotStatus, STOPPED, installation.getAssignment());
        return slotStatus;
    }

    @Override
    public SlotStatus terminate()
    {
        if (slotStatus.getState() == STOPPED) {
            slotStatus = slotStatus.updateState(TERMINATED);
        }
        return slotStatus;
    }

    @Override
    public SlotStatus start()
    {
        if (slotStatus.getAssignment() == null) {
            throw new IllegalStateException("Slot can not be started because the slot is not assigned");
        }
        slotStatus = slotStatus.updateState(RUNNING);
        return slotStatus;
    }

    @Override
    public SlotStatus restart()
    {
        if (slotStatus.getAssignment() == null) {
            throw new IllegalStateException("Slot can not be restarted because the slot is not assigned");
        }
        slotStatus = slotStatus.updateState(RUNNING);
        return slotStatus;
    }

    @Override
    public SlotStatus stop()
    {
        if (slotStatus.getAssignment() == null) {
            throw new IllegalStateException("Slot can not be stopped because the slot is not assigned");
        }
        slotStatus = slotStatus.updateState(STOPPED);
        return slotStatus;
    }
}
