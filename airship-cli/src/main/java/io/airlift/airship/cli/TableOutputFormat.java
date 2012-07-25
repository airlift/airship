package com.proofpoint.galaxy.cli;

import com.google.common.collect.Iterables;
import com.proofpoint.galaxy.shared.AgentStatusRepresentation;
import com.proofpoint.galaxy.shared.CoordinatorStatusRepresentation;
import com.proofpoint.galaxy.shared.SlotStatusRepresentation;

import static com.proofpoint.galaxy.cli.AgentRecord.toAgentRecords;
import static com.proofpoint.galaxy.cli.Column.externalHost;
import static com.proofpoint.galaxy.cli.Column.instanceType;
import static com.proofpoint.galaxy.cli.Column.internalHost;
import static com.proofpoint.galaxy.cli.Column.machine;
import static com.proofpoint.galaxy.cli.Column.shortBinary;
import static com.proofpoint.galaxy.cli.Column.shortConfig;
import static com.proofpoint.galaxy.cli.Column.shortId;
import static com.proofpoint.galaxy.cli.Column.shortLocation;
import static com.proofpoint.galaxy.cli.Column.status;
import static com.proofpoint.galaxy.cli.Column.statusMessage;
import static com.proofpoint.galaxy.cli.CoordinatorRecord.toCoordinatorRecords;
import static com.proofpoint.galaxy.cli.SlotRecord.toSlotRecords;

public class TableOutputFormat implements OutputFormat
{
    private final String environmentRef;
    private final Config config;

    public TableOutputFormat(String environmentRef, Config config)
    {
        this.environmentRef = environmentRef;
        this.config = config;
    }

    @Override
    public void displaySlots(Iterable<SlotStatusRepresentation> slots)
    {
        if (Iterables.isEmpty(slots)) {
            System.out.println("No slots match the provided filters.");
        }
        else {
            TablePrinter tablePrinter = new TablePrinter(shortId, getHostColumn(), machine, status, shortBinary, shortConfig, statusMessage);
            tablePrinter.print(toSlotRecords(slots));
        }
    }

    @Override
    public void displayAgents(Iterable<AgentStatusRepresentation> agents)
    {
        if (Iterables.isEmpty(agents)) {
            System.out.println("No agents match the provided filters.");
        }
        else {
            TablePrinter tablePrinter = new TablePrinter(shortId, getHostColumn(), machine, status, instanceType, shortLocation);
            tablePrinter.print(toAgentRecords(agents));
        }
    }

    @Override
    public void displayCoordinators(Iterable<CoordinatorStatusRepresentation> coordinators)
    {
        if (Iterables.isEmpty(coordinators)) {
            System.out.println("No coordinators match the provided filters.");
        }
        else {
            // todo add short id once coordinator is update to get coordinator id from remote coordinators
            TablePrinter tablePrinter = new TablePrinter(machine, getHostColumn(), status, instanceType, shortLocation);
            tablePrinter.print(toCoordinatorRecords(coordinators));
        }
    }

    private Column getHostColumn()
    {
        if ("true".equalsIgnoreCase(config.get("environment." + environmentRef + ".use-internal-address"))) {
            return internalHost;
        } else {
            return externalHost;
        }
    }
}
