package com.proofpoint.galaxy.cli;

import com.google.common.base.Function;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.proofpoint.galaxy.shared.CoordinatorLifecycleState;
import com.proofpoint.galaxy.shared.CoordinatorStatusRepresentation;
import org.fusesource.jansi.Ansi.Color;

import static com.proofpoint.galaxy.cli.Ansi.colorize;

public class CoordinatorRecord implements Record
{
    public static ImmutableList<Record> toCoordinatorRecords(Iterable<CoordinatorStatusRepresentation> coordinators)
    {
        return ImmutableList.copyOf(Iterables.transform(coordinators, new Function<CoordinatorStatusRepresentation, Record>()
        {
            @Override
            public CoordinatorRecord apply(CoordinatorStatusRepresentation coordinator)
            {
                return new CoordinatorRecord(coordinator);
            }
        }));
    }

    private final CoordinatorStatusRepresentation coordinatorStatus;

    public CoordinatorRecord(CoordinatorStatusRepresentation coordinatorStatus)
    {
        Preconditions.checkNotNull(coordinatorStatus, "coordinatorStatus is null");

        this.coordinatorStatus = coordinatorStatus;
    }

    public Object getObjectValue(Column column)
    {
        switch (column) {
            case shortId:
                return coordinatorStatus.getCoordinatorId();
            case uuid:
                return coordinatorStatus.getCoordinatorId();
            case host:
                return coordinatorStatus.getHost();
            case ip:
                return coordinatorStatus.getIp();
            case status:
                return coordinatorStatus.getState();
            case location:
                return coordinatorStatus.getLocation();
            case instanceType:
                return coordinatorStatus.getInstanceType();
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
        Object value = getObjectValue(column);
        if (Column.status == column) {
            CoordinatorLifecycleState state = CoordinatorLifecycleState.valueOf(toString(value));
            if (CoordinatorLifecycleState.ONLINE == state) {
                return colorize(state, Color.GREEN);
            } else if (CoordinatorLifecycleState.OFFLINE == state) {
                return colorize(state, Color.RED);
            } else if (CoordinatorLifecycleState.PROVISIONING == state) {
                return colorize(state, Color.BLUE);
            }
        } else if (Column.statusMessage == column) {
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
