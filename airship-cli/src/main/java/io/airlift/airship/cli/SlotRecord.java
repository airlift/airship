package io.airlift.airship.cli;

import com.google.common.base.Function;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import io.airlift.airship.shared.SlotLifecycleState;
import io.airlift.airship.shared.SlotStatusRepresentation;
import org.fusesource.jansi.Ansi.Color;

import static io.airlift.airship.cli.Ansi.colorize;

public class SlotRecord
        implements Record
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
            case machine:
                return slotStatus.getInstanceId();
            case internalHost:
                return slotStatus.getInternalHost();
            case internalIp:
                return slotStatus.getInternalIp();
            case externalHost:
                return slotStatus.getExternalHost();
            case status:
                return slotStatus.getStatus();
            case location:
                return slotStatus.getLocation();
            case shortLocation:
                return slotStatus.getShortLocation();
            case binary:
                return slotStatus.getBinary();
            case shortBinary:
                return slotStatus.getShortBinary();
            case config:
                return slotStatus.getConfig();
            case shortConfig:
                return slotStatus.getShortConfig();
            case expectedStatus:
                return slotStatus.getExpectedStatus();
            case expectedBinary:
                return slotStatus.getExpectedBinary();
            case expectedConfig:
                return slotStatus.getExpectedConfig();
            case statusMessage:
                return slotStatus.getStatusMessage();
            default:
                return null;
        }
    }

    @Override
    public String getValue(Column column)
    {
        return toString(getObjectValue(column));
    }

    @Override
    public String getColorizedValue(Column column)
    {
        Object value = getObjectValue(column);
        if (Column.status == column) {
            SlotLifecycleState state = SlotLifecycleState.lookup(toString(value));
            if (SlotLifecycleState.RUNNING == state) {
                return colorize(state, Color.GREEN);
            }
            else if (SlotLifecycleState.UNKNOWN == state) {
                return colorize(state, Color.RED);
            }
        }
        else if (Column.statusMessage == column) {
            return colorize(value, Color.RED);
        }
        return toString(value);
    }

    private String toString(Object value)
    {
        if (value == null) {
            return "";
        }
        return String.valueOf(value);
    }
}
