package com.proofpoint.galaxy.cli;

import com.google.common.base.Predicate;
import com.google.common.collect.Lists;
import com.proofpoint.galaxy.coordinator.AgentFilterBuilder;
import com.proofpoint.galaxy.shared.AgentStatus;
import com.proofpoint.galaxy.shared.Assignment;
import com.proofpoint.galaxy.shared.HttpUriBuilder;
import com.proofpoint.galaxy.shared.Repository;
import org.iq80.cli.Option;

import java.net.URI;
import java.util.List;
import java.util.UUID;

import static com.google.common.collect.Lists.newArrayList;

public class AgentFilter
{
    @Option(name = {"-u", "--uuid"}, description = "Select agent with the given UUID")
    public final List<String> uuid = newArrayList();

    @Option(name = {"-h", "--host"}, description = "Select agent on the given host or ip")
    public final List<String> host = newArrayList();

    @Option(name = {"-m", "--machine"}, description = "Select agents on the given machine")
    public final List<String> machine = newArrayList();

    @Option(name = { "--slot-uuid"}, description = "Select agent containing a slot the given UUID")
    public final List<String> slotUuid = newArrayList();

    @Option(name = {"-s", "--state"}, description = "Select agent containing 'r{unning}', 's{topped}' or 'unknown' slots")
    public final List<String> state = newArrayList();

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
