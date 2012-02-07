package com.proofpoint.galaxy.cli;

import com.google.common.base.Predicate;
import com.proofpoint.galaxy.coordinator.AgentFilterBuilder;
import com.proofpoint.galaxy.shared.AgentStatus;
import com.proofpoint.galaxy.shared.HttpUriBuilder;
import org.iq80.cli.Option;

import java.net.URI;
import java.util.List;

import static com.google.common.collect.Lists.newArrayList;

public class AgentFilter
{
    @Option(name = {"-i", "--host"}, description = "Select agent on the given host")
    public final List<String> host = newArrayList();

    @Option(name = {"-I", "--ip"}, description = "Select agent at the given IP address")
    public final List<String> ip = newArrayList();

    @Option(name = {"-u", "--uuid"}, description = "Select agent containing a slot the given UUID")
    public final List<String> uuid = newArrayList();

    @Option(name = {"-s", "--state"}, description = "Select agent containing 'r{unning}', 's{topped}' or 'unknown' slots")
    public final List<String> state = newArrayList();

    public Predicate<AgentStatus> toAgentPredicate()
    {
        AgentFilterBuilder agentFilterBuilder = AgentFilterBuilder.builder();
        for (String hostGlob : host) {
            agentFilterBuilder.addHostGlobFilter(hostGlob);
        }
        for (String ipFilter : ip) {
            agentFilterBuilder.addIpFilter(ipFilter);
        }
        for (String stateFilter : state) {
            agentFilterBuilder.addStateFilter(stateFilter);
        }
        for (String uuidGlob : uuid) {
            agentFilterBuilder.addSlotUuidGlobFilter(uuidGlob);
        }
        return agentFilterBuilder.build();
    }

    @Override
    public String toString()
    {
        final StringBuilder sb = new StringBuilder();
        sb.append("Filter");
        sb.append("{host=").append(host);
        sb.append(", ip=").append(ip);
        sb.append(", uuid=").append(uuid);
        sb.append(", state=").append(state);
        sb.append('}');
        return sb.toString();
    }

    public URI toUri(URI baseUri)
    {
        HttpUriBuilder uriBuilder = HttpUriBuilder.uriBuilderFrom(baseUri);
        return toUri(uriBuilder);
    }

    public URI toUri(HttpUriBuilder uriBuilder)
    {
        for (String hostGlob : host) {
            uriBuilder.addParameter("host", hostGlob);
        }
        for (String ipFilter : ip) {
            uriBuilder.addParameter("ip", ipFilter);
        }
        for (String stateFilter : state) {
            uriBuilder.addParameter("state", stateFilter);
        }
        for (String shortId : uuid) {
            uriBuilder.addParameter("uuid", shortId);
        }
        return uriBuilder.build();
    }

}
