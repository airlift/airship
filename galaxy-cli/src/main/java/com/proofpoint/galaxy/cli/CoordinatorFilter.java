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
    @Option(name = {"-i", "--host"}, description = "Select coordinator on the given host")
    public final List<String> host = newArrayList();

    @Option(name = {"-I", "--ip"}, description = "Select coordinator at the given IP address")
    public final List<String> ip = newArrayList();

    @Option(name = {"-s", "--state"}, description = "Select coordinator containing 'r{unning}', 's{topped}' or 'unknown' slots")
    public final List<String> state = newArrayList();

    public Predicate<CoordinatorStatus> toCoordinatorPredicate()
    {
        CoordinatorFilterBuilder coordinatorFilterBuilder = CoordinatorFilterBuilder.builder();
        for (String hostGlob : host) {
            coordinatorFilterBuilder.addHostGlobFilter(hostGlob);
        }
        for (String ipFilter : ip) {
            coordinatorFilterBuilder.addIpFilter(ipFilter);
        }
        for (String stateFilter : state) {
            coordinatorFilterBuilder.addStateFilter(stateFilter);
        }
        return coordinatorFilterBuilder.build();
    }

    @Override
    public String toString()
    {
        final StringBuilder sb = new StringBuilder();
        sb.append("Filter");
        sb.append("{host=").append(host);
        sb.append(", ip=").append(ip);
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
        return uriBuilder.build();
    }

}
