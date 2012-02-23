package com.proofpoint.galaxy.shared;

import com.google.common.base.Function;

public class SlotStatusWithExpectedState
{
    private final SlotStatus slotStatus;
    private final ExpectedSlotStatus expectedSlotStatus;

    public SlotStatusWithExpectedState(SlotStatus slotStatus, ExpectedSlotStatus expectedSlotStatus)
    {
        this.slotStatus = slotStatus;
        this.expectedSlotStatus = expectedSlotStatus;
    }

    public SlotStatus getSlotStatus()
    {
        return slotStatus;
    }

    public ExpectedSlotStatus getExpectedSlotStatus()
    {
        return expectedSlotStatus;
    }

    @Override
    public String toString()
    {
        final StringBuilder sb = new StringBuilder();
        sb.append("SlotStatusWithExpectedState");
        sb.append("{slotStatus=").append(slotStatus);
        sb.append(", expectedSlotStatus=").append(expectedSlotStatus);
        sb.append('}');
        return sb.toString();
    }

    public static Function<SlotStatusWithExpectedState, SlotStatus> toSlotStatus() {
        return new Function<SlotStatusWithExpectedState, SlotStatus>()
        {
            @Override
            public SlotStatus apply(SlotStatusWithExpectedState input)
            {
                return input.getSlotStatus();
            }
        };
    }
}

