package com.proofpoint.galaxy.console;

import com.google.common.base.Preconditions;
import com.proofpoint.galaxy.LifecycleState;
import com.proofpoint.galaxy.SlotStatus;
import com.proofpoint.galaxy.agent.Assignment;

import java.net.URI;
import java.util.UUID;

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
    public String getName()
    {
        return slotStatus.getName();
    }

    @Override
    public URI getSelf()
    {
        return slotStatus.getSelf();
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
    public SlotStatus assign(Assignment assignment)
    {
        Preconditions.checkNotNull(assignment, "assignment is null");
        slotStatus = new SlotStatus(slotStatus.getId(),
                slotStatus.getName(),
                slotStatus.getSelf(),
                assignment.getBinary(),
                assignment.getConfig(),
                LifecycleState.STOPPED);
        return slotStatus;
    }

    @Override
    public SlotStatus clear()
    {
        slotStatus = new SlotStatus(slotStatus.getId(), slotStatus.getName(), slotStatus.getSelf(), LifecycleState.UNASSIGNED);
        return slotStatus;
    }

    @Override
    public SlotStatus start()
    {
        if (slotStatus.getBinary() == null) {
            throw new IllegalStateException("Slot can not be started because the slot is not assigned");
        }
        slotStatus = new SlotStatus(slotStatus.getId(),
                slotStatus.getName(),
                slotStatus.getSelf(),
                slotStatus.getBinary(),
                slotStatus.getConfig(),
                LifecycleState.RUNNING);
        return slotStatus;
    }

    @Override
    public SlotStatus restart()
    {
        if (slotStatus.getBinary() == null) {
            throw new IllegalStateException("Slot can not be restarted because the slot is not assigned");
        }
        slotStatus = new SlotStatus(slotStatus.getId(),
                slotStatus.getName(),
                slotStatus.getSelf(),
                slotStatus.getBinary(),
                slotStatus.getConfig(),
                LifecycleState.RUNNING);
        return slotStatus;
    }

    @Override
    public SlotStatus stop()
    {
        if (slotStatus.getBinary() == null) {
            throw new IllegalStateException("Slot can not be stopped because the slot is not assigned");
        }
        slotStatus = new SlotStatus(slotStatus.getId(),
                slotStatus.getName(),
                slotStatus.getSelf(),
                slotStatus.getBinary(),
                slotStatus.getConfig(),
                LifecycleState.STOPPED);
        return slotStatus;
    }
}
