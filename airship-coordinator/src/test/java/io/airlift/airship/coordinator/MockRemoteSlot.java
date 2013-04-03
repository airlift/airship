package io.airlift.airship.coordinator;

import com.google.common.base.Preconditions;
import io.airlift.airship.shared.SlotStatus;
import io.airlift.airship.shared.Installation;

import java.util.UUID;

import static io.airlift.airship.shared.SlotLifecycleState.RUNNING;
import static io.airlift.airship.shared.SlotLifecycleState.STOPPED;
import static io.airlift.airship.shared.SlotLifecycleState.TERMINATED;

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
        slotStatus = slotStatus.changeAssignment(STOPPED, installation.getAssignment(), slotStatus.getResources());
        mockRemoteAgent.setSlotStatus(slotStatus);
        return slotStatus;
    }

    @Override
    public SlotStatus terminate()
    {
        if (slotStatus.getState() == STOPPED) {
            slotStatus = slotStatus.changeState(TERMINATED);
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
        slotStatus = slotStatus.changeState(RUNNING);
        mockRemoteAgent.setSlotStatus(slotStatus);
        return slotStatus;
    }

    @Override
    public SlotStatus restart()
    {
        if (slotStatus.getAssignment() == null) {
            throw new IllegalStateException("Slot can not be restarted because the slot is not assigned");
        }
        slotStatus = slotStatus.changeState(RUNNING);
        mockRemoteAgent.setSlotStatus(slotStatus);
        return slotStatus;
    }

    @Override
    public SlotStatus stop()
    {
        if (slotStatus.getAssignment() == null) {
            throw new IllegalStateException("Slot can not be stopped because the slot is not assigned");
        }
        slotStatus = slotStatus.changeState(STOPPED);
        mockRemoteAgent.setSlotStatus(slotStatus);
        return slotStatus;
    }

    @Override
    public SlotStatus kill()
    {
        return stop();
    }
}
