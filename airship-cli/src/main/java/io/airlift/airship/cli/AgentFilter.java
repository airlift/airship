package io.airlift.airship.cli;

import com.google.common.base.Predicate;
import com.google.common.collect.Lists;
import io.airlift.airline.Option;
import io.airlift.airship.coordinator.AgentFilterBuilder;
import io.airlift.airship.shared.AgentStatus;
import io.airlift.airship.shared.Assignment;
import io.airlift.airship.shared.HttpUriBuilder;
import io.airlift.airship.shared.Repository;

import java.net.URI;
import java.util.List;
import java.util.UUID;

import static com.google.common.collect.Lists.newArrayList;

public class AgentFilter
{
    @Option(name = {"-u", "--uuid"}, description = "Select agent with the given UUID")
    public final List<String> uuid = newArrayList();

    @Option(name = {"-U", "--not-uuid"}, description = "Excludes agent with the given UUID")
    public final List<String> notUuid = newArrayList();

    @Option(name = {"-h", "--host"}, description = "Select agent on the given host or ip")
    public final List<String> host = newArrayList();

    @Option(name = {"-H", "--not-host"}, description = "Excludes agent on the given host or ip")
    public final List<String> notHost = newArrayList();

    @Option(name = {"-m", "--machine"}, description = "Select agent on the given machine")
    public final List<String> machine = newArrayList();

    @Option(name = {"-M", "--not-machine"}, description = "Excludes agent on the given machine")
    public final List<String> notMachine = newArrayList();

    @Option(name = {"--slot-uuid"}, description = "Select agent agent a slot the given UUID")
    public final List<String> slotUuid = newArrayList();

    @Option(name = {"--not-slot-uuid"}, description = "Exclude agent containing a slot the given UUID")
    public final List<String> notSlotUuid = newArrayList();

    @Option(name = {"-s", "--state"}, description = "Select 'online', 'offline' or 'provisioning' agents")
    public final List<String> state = newArrayList();

    @Option(name = {"-S", "--not-state"}, description = "Exclude 'online', 'offline' or 'provisioning' agents")
    public final List<String> notState = newArrayList();

    @Option(name = "--all", description = "Select all agents")
    public boolean selectAll;

    // assignable filters can not be set via the CLI
    public final List<Assignment> assignableFilters = Lists.newArrayList();

    public Predicate<AgentStatus> toAgentPredicate(List<String> allAgentUuids, List<UUID> allSlotUuids, boolean allowDuplicateInstallationsOnAnAgent, Repository repository)
    {
        return createFilterBuilder().build(allAgentUuids, allSlotUuids, allowDuplicateInstallationsOnAnAgent, repository);
    }

    public URI toUri(URI baseUri)
    {
        return createFilterBuilder().buildUri(baseUri);
    }

    public URI toUri(HttpUriBuilder uriBuilder)
    {
        return createFilterBuilder().buildUri(uriBuilder);
    }

    private AgentFilterBuilder createFilterBuilder()
    {
        AgentFilterBuilder agentFilterBuilder = AgentFilterBuilder.builder();
        for (String id : uuid) {
            agentFilterBuilder.addUuidFilter(id);
        }
        for (String hostGlob : host) {
            agentFilterBuilder.addHostGlobFilter(hostGlob);
        }
        for (String machineGlob : machine) {
            agentFilterBuilder.addMachineGlobFilter(machineGlob);
        }
        for (String stateFilter : state) {
            agentFilterBuilder.addStateFilter(stateFilter);
        }
        for (String slotUuidGlob : slotUuid) {
            agentFilterBuilder.addSlotUuidGlobFilter(slotUuidGlob);
        }
        for (Assignment assignment : assignableFilters) {
            agentFilterBuilder.addAssignableFilter(assignment);
        }
        if (selectAll) {
            agentFilterBuilder.selectAll();
        }
        return agentFilterBuilder;
    }

    @Override
    public String toString()
    {
        final StringBuilder sb = new StringBuilder();
        sb.append("AgentFilter");
        sb.append("{uuid=").append(uuid);
        sb.append(", host=").append(host);
        sb.append(", machine=").append(machine);
        sb.append(", slotUuid=").append(slotUuid);
        sb.append(", state=").append(state);
        sb.append(", assignableFilters=").append(assignableFilters);
        sb.append(", selectAll=").append(selectAll);
        sb.append('}');
        return sb.toString();
    }
}
