package io.airlift.airship.cli;

import com.google.common.base.Objects;
import com.google.common.base.Predicate;
import io.airlift.airship.coordinator.CoordinatorFilterBuilder;
import io.airlift.airship.shared.CoordinatorStatus;
import io.airlift.airship.shared.HttpUriBuilder;
import io.airlift.command.Option;

import java.net.URI;
import java.util.List;

import static com.google.common.collect.Lists.newArrayList;

public class CoordinatorFilter
{
    @Option(name = {"-u", "--uuid"}, description = "Select coordinator with the given UUID")
    public final List<String> uuid = newArrayList();

    @Option(name = {"-U", "--not-uuid"}, description = "Excludes coordinator with the given UUID")
    public final List<String> notUuid = newArrayList();

    @Option(name = {"-h", "--host"}, description = "Select coordinator on the given host or IP address")
    public final List<String> host = newArrayList();

    @Option(name = {"-H", "--not-host"}, description = "Excludes coordinator on the given host or IP address")
    public final List<String> notHost = newArrayList();

    @Option(name = {"-m", "--machine"}, description = "Select coordinator on the given machine")
    public final List<String> machine = newArrayList();

    @Option(name = {"-M", "--not-machine"}, description = "Excludes coordinator on the given machine")
    public final List<String> notMachine = newArrayList();

    @Option(name = {"-s", "--state"}, description = "Select 'online', 'offline' or 'provisioning' coordinators")
    public final List<String> state = newArrayList();

    @Option(name = {"-S", "--not-state"}, description = "Excludes 'online', 'offline' or 'provisioning' coordinators")
    public final List<String> notState = newArrayList();

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
        for (String notId : notUuid) {
            coordinatorFilterBuilder.addNotUuidFilter(notId);
        }
        for (String hostGlob : host) {
            coordinatorFilterBuilder.addHostGlobFilter(hostGlob);
        }
        for (String notHostGlob : notHost) {
            coordinatorFilterBuilder.addNotHostGlobFilter(notHostGlob);
        }
        for (String machineGlob : machine) {
            coordinatorFilterBuilder.addMachineGlobFilter(machineGlob);
        }
        for (String notMachineGlob : notMachine) {
            coordinatorFilterBuilder.addNotMachineGlobFilter(notMachineGlob);
        }
        for (String stateFilter : state) {
            coordinatorFilterBuilder.addStateFilter(stateFilter);
        }
        for (String notStateFilter : notState) {
            coordinatorFilterBuilder.addNotStateFilter(notStateFilter);
        }
        if (selectAll) {
            coordinatorFilterBuilder.selectAll();
        }
        return coordinatorFilterBuilder;
    }

    @Override
    public String toString()
    {
        return Objects.toStringHelper(this)
                .add("uuid", uuid)
                .add("notUuid", notUuid)
                .add("host", host)
                .add("notHost", notHost)
                .add("machine", machine)
                .add("notMachine", notMachine)
                .add("state", state)
                .add("notState", notState)
                .add("selectAll", selectAll)
                .toString();
    }
}
