package com.proofpoint.galaxy.standalone;

import com.google.common.base.Predicate;
import com.proofpoint.galaxy.coordinator.SlotFilterBuilder;
import com.proofpoint.galaxy.shared.SlotStatus;
import org.iq80.cli.Option;

import javax.ws.rs.core.UriBuilder;
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

    @Option(name = {"-i", "--host"}, description = "Select slots on the given host")
    public final List<String> host = newArrayList();

    @Option(name = {"-I", "--ip"}, description = "Select slots at the given IP address")
    public final List<String> ip = newArrayList();

    @Option(name = {"-u", "--uuid"}, description = "Select slot with the given UUID")
    public final List<String> uuid = newArrayList();

    @Option(name = {"-s", "--state"}, description = "Select 'r{unning}', 's{topped}' or 'unknown' slots")
    public final List<String> state = newArrayList();

    public Predicate<SlotStatus> toSlotPredicate(boolean filterRequired, List<UUID> allUuids)
    {
        SlotFilterBuilder slotFilterBuilder = SlotFilterBuilder.builder(filterRequired);
        for (String binaryGlob : binary) {
            slotFilterBuilder.addBinaryGlobFilter(binaryGlob);
        }
        for (String configGlob : binary) {
            slotFilterBuilder.addConfigGlobFilter(configGlob);
        }
        for (String hostGlob : host) {
            slotFilterBuilder.addHostGlobFilter(hostGlob);
        }
        for (String ipFilter : ip) {
            slotFilterBuilder.addIpFilter(ipFilter);
        }
        for (String stateFilter : state) {
            slotFilterBuilder.addStateFilter(stateFilter);
        }
        for (String shortId : uuid) {
            slotFilterBuilder.addSlotUuidFilter(shortId, allUuids);
        }
        return slotFilterBuilder.build();
    }

    public URI toUri(URI baseUri)
    {
        UriBuilder uriBuilder = UriBuilder.fromUri(baseUri);
        return toUri(uriBuilder);
    }

    public URI toUri(UriBuilder uriBuilder)
    {
        for (String binaryGlob : binary) {
            uriBuilder.queryParam("binary", binaryGlob);
        }
        for (String configGlob : config) {
            uriBuilder.queryParam("config", configGlob);
        }
        for (String hostGlob : host) {
            uriBuilder.queryParam("host", hostGlob);
        }
        for (String ipFilter : ip) {
            uriBuilder.queryParam("ip", ipFilter);
        }
        for (String stateFilter : state) {
            uriBuilder.queryParam("state", stateFilter);
        }
        for (String shortId : uuid) {
            uriBuilder.queryParam("uuid", shortId);
        }
        return uriBuilder.build();
    }

    @Override
    public String toString()
    {
        final StringBuilder sb = new StringBuilder();
        sb.append("Filter");
        sb.append("{binary=").append(binary);
        sb.append(", config=").append(config);
        sb.append(", host=").append(host);
        sb.append(", ip=").append(ip);
        sb.append(", uuid=").append(uuid);
        sb.append(", state=").append(state);
        sb.append('}');
        return sb.toString();
    }
}
