package io.airlift.airship.cli;

import com.google.common.base.Predicate;
import io.airlift.airship.coordinator.SlotFilterBuilder;
import io.airlift.airship.shared.HttpUriBuilder;
import io.airlift.airship.shared.SlotStatus;
import io.airlift.command.Option;

import java.net.URI;
import java.util.List;
import java.util.UUID;

import static com.google.common.collect.Lists.newArrayList;

public class SlotFilter
{
    @Option(name = {"-b", "--binary"}, description = "Select slots with a given binary")
    public final List<String> binary = newArrayList();

    @Option(name = {"-c", "--config"}, description = "Select slots with a given configuration")
    public final List<String> config = newArrayList();

    @Option(name = {"-h", "--host"}, description = "Select slots on the given host")
    public final List<String> host = newArrayList();

    @Option(name = {"-m", "--machine"}, description = "Select agents on the given machine")
    public final List<String> machine = newArrayList();

    @Option(name = {"-u", "--uuid"}, description = "Select slot with the given UUID")
    public final List<String> uuid = newArrayList();

    @Option(name = {"-s", "--state"}, description = "Select 'r{unning}', 's{topped}' or 'unknown' slots")
    public final List<String> state = newArrayList();

    @Option(name = "--all", description = "Select all slots")
    public boolean selectAll;

    public boolean isFiltered()
    {
        return !binary.isEmpty() ||
                !config.isEmpty() ||
                !host.isEmpty() ||
                !uuid.isEmpty() ||
                !state.isEmpty() ||
                selectAll;
    }

    public Predicate<SlotStatus> toSlotPredicate(boolean filterRequired, List<UUID> allUuids)
    {
        return createFilterBuilder().buildPredicate(filterRequired, allUuids);
    }

    public URI toUri(URI baseUri)
    {
        return createFilterBuilder().buildUri(baseUri);
    }

    public URI toUri(HttpUriBuilder uriBuilder)
    {
        return createFilterBuilder().buildUri(uriBuilder);
    }

    private SlotFilterBuilder createFilterBuilder()
    {
        SlotFilterBuilder slotFilterBuilder = SlotFilterBuilder.builder();
        for (String binaryGlob : binary) {
            slotFilterBuilder.addBinaryGlobFilter(binaryGlob);
        }
        for (String configGlob : config) {
            slotFilterBuilder.addConfigGlobFilter(configGlob);
        }
        for (String hostGlob : host) {
            slotFilterBuilder.addHostGlobFilter(hostGlob);
        }
        for (String machineGlob : machine) {
            slotFilterBuilder.addMachineGlobFilter(machineGlob);
        }
        for (String stateFilter : state) {
            slotFilterBuilder.addStateFilter(stateFilter);
        }
        for (String shortId : uuid) {
            slotFilterBuilder.addSlotUuidFilter(shortId);
        }
        if (selectAll) {
            slotFilterBuilder.selectAll();
        }
        return slotFilterBuilder;
    }

    @Override
    public String toString()
    {
        final StringBuilder sb = new StringBuilder();
        sb.append("Filter");
        sb.append("{binary=").append(binary);
        sb.append(", config=").append(config);
        sb.append(", host=").append(host);
        sb.append(", machine=").append(machine);
        sb.append(", uuid=").append(uuid);
        sb.append(", state=").append(state);
        sb.append(", selectAll=").append(selectAll);
        sb.append('}');
        return sb.toString();
    }
}
