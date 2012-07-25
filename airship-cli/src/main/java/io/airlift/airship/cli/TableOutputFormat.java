package io.airlift.airship.cli;

import com.google.common.collect.Iterables;
import io.airlift.airship.shared.AgentStatusRepresentation;
import io.airlift.airship.shared.CoordinatorStatusRepresentation;
import io.airlift.airship.shared.SlotStatusRepresentation;

import static io.airlift.airship.cli.AgentRecord.toAgentRecords;
import static io.airlift.airship.cli.Column.externalHost;
import static io.airlift.airship.cli.Column.instanceType;
import static io.airlift.airship.cli.Column.internalHost;
import static io.airlift.airship.cli.Column.machine;
import static io.airlift.airship.cli.Column.shortBinary;
import static io.airlift.airship.cli.Column.shortConfig;
import static io.airlift.airship.cli.Column.shortId;
import static io.airlift.airship.cli.Column.shortLocation;
import static io.airlift.airship.cli.Column.status;
import static io.airlift.airship.cli.Column.statusMessage;
import static io.airlift.airship.cli.CoordinatorRecord.toCoordinatorRecords;
import static io.airlift.airship.cli.SlotRecord.toSlotRecords;

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
