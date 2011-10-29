package com.proofpoint.galaxy.standalone;

import com.google.common.base.Function;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.proofpoint.galaxy.shared.SlotStatusWithExpectedState;
import com.proofpoint.galaxy.shared.SlotStatus;
import com.proofpoint.galaxy.shared.SlotStatusRepresentation;

public class SlotRecord implements Record
{

    public static ImmutableList<Record> toSlotRecords(Iterable<SlotStatusRepresentation> slots)
    {
        return ImmutableList.copyOf(Iterables.transform(slots, new Function<SlotStatusRepresentation, Record>()
        {
            @Override
            public SlotRecord apply(SlotStatusRepresentation slot)
            {
                return new SlotRecord(slot);
            }
        }));
    }

    public static ImmutableList<Record> toSlotRecords(final int prefixSize, Iterable<SlotStatus> slots)
    {
        return ImmutableList.copyOf(Iterables.transform(slots, new Function<SlotStatus, Record>()
        {
            @Override
            public SlotRecord apply(SlotStatus slot)
            {
                return new SlotRecord(SlotStatusRepresentation.from(slot, prefixSize));
            }
        }));
    }

    public static ImmutableList<Record> toSlotRecordsWithExpectedState(final int prefixSize, Iterable<SlotStatusWithExpectedState> slots)
    {
        return ImmutableList.copyOf(Iterables.transform(slots, new Function<SlotStatusWithExpectedState, Record>()
        {
            @Override
            public SlotRecord apply(SlotStatusWithExpectedState slot)
            {
                return new SlotRecord(SlotStatusRepresentation.from(slot, prefixSize));
            }
        }));
    }

    private final SlotStatusRepresentation slotStatus;

    public SlotRecord(SlotStatusRepresentation statusRepresentation)
    {
        this.slotStatus = statusRepresentation;
    }

    public String getObjectValue(Column column)
    {
        switch (column) {
            case shortId:
                return slotStatus.getShortId();
            case uuid:
                return slotStatus.getId().toString();
            case host:
                return slotStatus.getHost();
            case ip:
                return slotStatus.getIp();
            case status:
                return slotStatus.getStatus();
            case binary:
                return slotStatus.getBinary();
            case config:
                return slotStatus.getConfig();
            case expectedStatus:
                return slotStatus.getExpectedStatus();
            case expectedBinary:
                return slotStatus.getExpectedBinary();
            case expectedConfig:
                return slotStatus.getExpectedConfig();
            default:
                return null;
        }
    }

    @Override
    public String getValue(Column column)
    {
        Object value = getObjectValue(column);
        if (value == null) {
            return "";
        }
        return String.valueOf(value);
    }

    @Override
    public String getColorizedValue(Column column)
    {
        return getValue(column);
    }
}
