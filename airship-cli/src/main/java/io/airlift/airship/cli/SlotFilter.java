package io.airlift.airship.cli;

import com.google.common.base.Objects;
import com.google.common.base.Predicate;
import com.google.common.collect.Iterables;
import io.airlift.airship.coordinator.SlotFilterBuilder;
import io.airlift.airship.shared.HttpUriBuilder;
import io.airlift.airship.shared.SlotStatus;
import io.airlift.command.Option;

import javax.annotation.Nullable;
import java.net.URI;
import java.util.List;
import java.util.UUID;

import static com.google.common.base.Predicates.not;
import static com.google.common.collect.Lists.newArrayList;
import static java.util.Arrays.asList;

public class SlotFilter
{
    @Option(name = {"-b", "--binary"}, description = "Select slots with a given binary")
    public final List<String> binary = newArrayList();

    @Option(name = {"-B", "--not-binary"}, description = "Excludes slots with a given binary")
    public final List<String> notBinary = newArrayList();

    @Option(name = {"-c", "--config"}, description = "Select slots with a given configuration")
    public final List<String> config = newArrayList();

    @Option(name = {"-C", "--not-config"}, description = "Excludes slots with a given configuration")
    public final List<String> notConfig = newArrayList();

    @Option(name = {"-h", "--host"}, description = "Select slots on the given host or ip")
    public final List<String> host = newArrayList();

    @Option(name = {"-H", "--not-host"}, description = "Excludes slots on the given host or ip")
    public final List<String> notHost = newArrayList();

    @Option(name = {"-m", "--machine"}, description = "Select agents on the given machine")
    public final List<String> machine = newArrayList();

    @Option(name = {"-M", "--not-machine"}, description = "Excludes agents on the given machine")
    public final List<String> notMachine = newArrayList();

    @Option(name = {"-u", "--uuid"}, description = "Select slot with the given UUID")
    public final List<String> uuid = newArrayList();

    @Option(name = {"-U", "--not-uuid"}, description = "Excludes slot with the given UUID")
    public final List<String> notUuid = newArrayList();

    @Option(name = {"-s", "--state"}, description = "Select 'r{unning}', 's{topped}' or 'u{nknown}' slots")
    public final List<String> state = newArrayList();

    @Option(name = {"-S", "--not-state"}, description = "Excludes 'r{unning}', 's{topped}' or 'u{nknown}' slots")
    public final List<String> notState = newArrayList();

    @Option(name = "--all", description = "Select all slots")
    public boolean selectAll;

    public boolean isFiltered()
    {
        return selectAll || Iterables.any(asList(binary, notBinary, config, notConfig, host, notHost, uuid, notUuid, state, notState), not(emptyPredicate()));
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
        for (String notBinaryGlob : notBinary) {
            slotFilterBuilder.addNotBinaryGlobFilter(notBinaryGlob);
        }
        for (String configGlob : config) {
            slotFilterBuilder.addConfigGlobFilter(configGlob);
        }
        for (String notConfigGlob : notConfig) {
            slotFilterBuilder.addNotConfigGlobFilter(notConfigGlob);
        }
        for (String hostGlob : host) {
            slotFilterBuilder.addHostGlobFilter(hostGlob);
        }
        for (String notHostGlob : notHost) {
            slotFilterBuilder.addNotHostGlobFilter(notHostGlob);
        }
        for (String machineGlob : machine) {
            slotFilterBuilder.addMachineGlobFilter(machineGlob);
        }
        for (String notMachineGlob : notMachine) {
            slotFilterBuilder.addNotMachineGlobFilter(notMachineGlob);
        }
        for (String stateFilter : state) {
            slotFilterBuilder.addStateFilter(stateFilter);
        }
        for (String notStateFilter : notState) {
            slotFilterBuilder.addNotStateFilter(notStateFilter);
        }
        for (String shortId : uuid) {
            slotFilterBuilder.addSlotUuidFilter(shortId);
        }
        for (String notShortId : notUuid) {
            slotFilterBuilder.addNotSlotUuidFilter(notShortId);
        }
        if (selectAll) {
            slotFilterBuilder.selectAll();
        }
        return slotFilterBuilder;
    }

    @Override
    public String toString()
    {
        return Objects.toStringHelper(this)
                .add("binary", binary)
                .add("notBinary", notBinary)
                .add("config", config)
                .add("notConfig", notConfig)
                .add("host", host)
                .add("notHost", notHost)
                .add("machine", machine)
                .add("notMachine", notMachine)
                .add("uuid", uuid)
                .add("notUuid", notUuid)
                .add("state", state)
                .add("notState", notState)
                .add("selectAll", selectAll)
                .toString();
    }

    private static Predicate<List<?>> emptyPredicate()
    {
        return new Predicate<List<?>>() {
            @Override
            public boolean apply(@Nullable List<?> input)
            {
                return input.isEmpty();
            }
        };
    }
}
