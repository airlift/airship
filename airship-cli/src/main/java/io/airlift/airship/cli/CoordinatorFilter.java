package com.proofpoint.galaxy.cli;

import com.google.common.base.Predicate;
import com.proofpoint.galaxy.coordinator.CoordinatorFilterBuilder;
import com.proofpoint.galaxy.shared.CoordinatorStatus;
import com.proofpoint.galaxy.shared.HttpUriBuilder;
import org.iq80.cli.Option;

import java.net.URI;
import java.util.List;

import static com.google.common.collect.Lists.newArrayList;

public class CoordinatorFilter
{
    @Option(name = {"-u", "--uuid"}, description = "Select coordinator with the given UUID")
    public final List<String> uuid = newArrayList();

    @Option(name = {"-h", "--host"}, description = "Select coordinator on the given host or IP address")
    public final List<String> host = newArrayList();

    @Option(name = {"-m", "--machine"}, description = "Select coordinator on the given machine")
    public final List<String> machine = newArrayList();

    @Option(name = {"-s", "--state"}, description = "Select coordinator containing 'r{unning}', 's{topped}' or 'unknown' slots")
    public final List<String> state = newArrayList();

    @Option(name = "--all", description = "Select all coordinators")
    public boolean selectAll;

    public Predicate<CoordinatorStatus> toCoordinatorPredicate()
    {
        return createFilterBuilder().buildPredicate();
    }

    public URI toUri(URI baseUri)
    {
        return createFilterBuilder().buildUri(baseUri);
    }

    public URI toUri(HttpUriBuilder uriBuilder)
    {
        return createFilterBuilder().buildUri(uriBuilder);
    }

    private CoordinatorFilterBuilder createFilterBuilder()
    {
        CoordinatorFilterBuilder coordinatorFilterBuilder = CoordinatorFilterBuilder.builder();
        for (String id : uuid) {
            coordinatorFilterBuilder.addUuidFilter(id);
        }
        for (String hostGlob : host) {
            coordinatorFilterBuilder.addHostGlobFilter(hostGlob);
        }
        for (String machineGlob : machine) {
            coordinatorFilterBuilder.addMachineGlobFilter(machineGlob);
        }
        for (String stateFilter : state) {
            coordinatorFilterBuilder.addStateFilter(stateFilter);
        }
        if (selectAll) {
            coordinatorFilterBuilder.selectAll();
        }
        return coordinatorFilterBuilder;
    }

    @Override
    public String toString()
    {
        final StringBuilder sb = new StringBuilder();
        sb.append("CoordinatorFilter");
        sb.append("{uuid=").append(uuid);
        sb.append(", host=").append(host);
        sb.append(", machine=").append(machine);
        sb.append(", state=").append(state);
        sb.append(", selectAll=").append(selectAll);
        sb.append('}');
        return sb.toString();
    }
}
