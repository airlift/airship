package com.proofpoint.galaxy.coordinator;

import com.google.common.base.Function;
import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.collect.Lists;
import com.proofpoint.galaxy.shared.AgentLifecycleState;
import com.proofpoint.galaxy.shared.AgentStatus;
import com.proofpoint.galaxy.shared.HttpUriBuilder;
import com.proofpoint.galaxy.shared.SlotStatus;

import javax.annotation.Nullable;
import javax.ws.rs.core.UriInfo;
import java.net.URI;
import java.util.List;
import java.util.Map.Entry;
import java.util.UUID;

public class AgentFilterBuilder
{
    public static AgentFilterBuilder builder()
    {
        return new AgentFilterBuilder();
    }

    public static Predicate<AgentStatus> build(UriInfo uriInfo, List<UUID> allUuids)
    {
        AgentFilterBuilder builder = new AgentFilterBuilder();
        for (Entry<String, List<String>> entry : uriInfo.getQueryParameters().entrySet()) {
            if ("state" .equals(entry.getKey())) {
                for (String stateFilter : entry.getValue()) {
                    builder.addStateFilter(stateFilter);
                }
            }
            else if ("host" .equals(entry.getKey())) {
                for (String hostGlob : entry.getValue()) {
                    builder.addHostGlobFilter(hostGlob);
                }
            }
            else if ("uuid" .equals(entry.getKey())) {
                for (String uuidGlob : entry.getValue()) {
                    builder.addSlotUuidGlobFilter(uuidGlob);
                }
            }
        }
        return builder.build(allUuids);
    }

    private final List<AgentLifecycleState> stateFilters = Lists.newArrayListWithCapacity(6);
    private final List<String> slotUuidGlobs = Lists.newArrayListWithCapacity(6);
    private final List<String> hostGlobs = Lists.newArrayListWithCapacity(6);

    public void addStateFilter(String stateFilter)
    {
        Preconditions.checkNotNull(stateFilter, "stateFilter is null");
        AgentLifecycleState state = AgentLifecycleState.valueOf(stateFilter.toUpperCase());
        Preconditions.checkArgument(state != null, "unknown state " + stateFilter);
        stateFilters.add(state);
    }

    public void addSlotUuidGlobFilter(String slotUuidGlob)
    {
        Preconditions.checkNotNull(slotUuidGlob, "slotUuidGlob is null");
        slotUuidGlobs.add(slotUuidGlob);
    }

    public void addHostGlobFilter(String hostGlob)
    {
        Preconditions.checkNotNull(hostGlob, "hostGlob is null");
        hostGlobs.add(hostGlob);
    }

    public Predicate<AgentStatus> build(final List<UUID> allUuids)
    {
        // Filters are evaluated as: set | host | (env & version & type)
        List<Predicate<AgentStatus>> andPredicates = Lists.newArrayListWithCapacity(6);
        if (!stateFilters.isEmpty()) {
            Predicate<AgentStatus> predicate = Predicates.or(Lists.transform(stateFilters, new Function<AgentLifecycleState, StatePredicate>()
            {
                @Override
                public StatePredicate apply(AgentLifecycleState state)
                {
                    return new StatePredicate(state);
                }
            }));
            andPredicates.add(predicate);
        }
        if (!slotUuidGlobs.isEmpty()) {
            Predicate<AgentStatus> predicate = Predicates.or(Lists.transform(slotUuidGlobs, new Function<String, SlotUuidPredicate>()
            {
                @Override
                public SlotUuidPredicate apply(String slotUuidGlob)
                {
                    return new SlotUuidPredicate(slotUuidGlob, allUuids);
                }
            }));
            andPredicates.add(predicate);
        }
        if (!hostGlobs.isEmpty()) {
            Predicate<AgentStatus> predicate = Predicates.or(Lists.transform(hostGlobs, new Function<String, HostPredicate>()
            {
                @Override
                public HostPredicate apply(String hostGlob)
                {
                    return new HostPredicate(hostGlob);
                }
            }));
            andPredicates.add(predicate);
        }
        if (!andPredicates.isEmpty()) {
            return Predicates.and(andPredicates);
        }
        else {
            return Predicates.alwaysTrue();
        }
    }

    public URI buildUri(URI baseUri)
    {
        HttpUriBuilder uriBuilder = HttpUriBuilder.uriBuilderFrom(baseUri);
        return buildUri(uriBuilder);
    }

    public URI buildUri(HttpUriBuilder uriBuilder)
    {
        for (String hostGlob : hostGlobs) {
            uriBuilder.addParameter("host", hostGlob);
        }
        for (AgentLifecycleState stateFilter : stateFilters) {
            uriBuilder.addParameter("state", stateFilter.name());
        }
        for (String shortId : slotUuidGlobs) {
            uriBuilder.addParameter("uuid", shortId);
        }
        return uriBuilder.build();
    }

    public static class SlotUuidPredicate implements Predicate<AgentStatus>
    {
        private final SlotFilterBuilder.SlotUuidPredicate predicate;

        public SlotUuidPredicate(UUID slotUuid)
        {
            predicate = new SlotFilterBuilder.SlotUuidPredicate(slotUuid);
        }

        public SlotUuidPredicate(String slotUuidGlobGlob, List<UUID> allUuids)
        {
            predicate = new SlotFilterBuilder.SlotUuidPredicate(slotUuidGlobGlob, allUuids);
        }

        @Override
        public boolean apply(@Nullable AgentStatus agentStatus)
        {
            if (agentStatus == null) {
                return false;
            }
            for (SlotStatus slotStatus : agentStatus.getSlotStatuses()) {
                if (predicate.apply(slotStatus)) {
                    return true;
                }
            }
            return false;
        }
    }

    public static class HostPredicate implements Predicate<AgentStatus>
    {
        private final UriHostPredicate predicate;

        public HostPredicate(String hostGlob)
        {
            predicate = new UriHostPredicate(hostGlob.toLowerCase());
        }

        @Override
        public boolean apply(@Nullable AgentStatus agentStatus)
        {
            return agentStatus != null &&
                    (predicate.apply(agentStatus.getExternalUri()) || predicate.apply(agentStatus.getInternalUri()));
        }
    }

    public static class StatePredicate implements Predicate<AgentStatus>
    {
        private final AgentLifecycleState state;

        public StatePredicate(AgentLifecycleState state)
        {
            this.state = state;
        }

        @Override
        public boolean apply(@Nullable AgentStatus agentStatus)
        {
            return agentStatus.getState() == state;
        }
    }
}
