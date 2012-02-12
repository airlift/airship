package com.proofpoint.galaxy.coordinator;

import com.google.common.base.Function;
import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.collect.Lists;
import com.proofpoint.galaxy.shared.CoordinatorLifecycleState;
import com.proofpoint.galaxy.shared.CoordinatorStatus;
import com.proofpoint.galaxy.shared.HttpUriBuilder;

import javax.annotation.Nullable;
import javax.ws.rs.core.UriInfo;
import java.net.URI;
import java.util.List;
import java.util.Map.Entry;

public class CoordinatorFilterBuilder
{
    public static CoordinatorFilterBuilder builder()
    {
        return new CoordinatorFilterBuilder();
    }

    public static Predicate<CoordinatorStatus> build(UriInfo uriInfo)
    {
        CoordinatorFilterBuilder builder = new CoordinatorFilterBuilder();
        for (Entry<String, List<String>> entry : uriInfo.getQueryParameters().entrySet()) {
            if ("state".equals(entry.getKey())) {
                for (String stateFilter : entry.getValue()) {
                    builder.addStateFilter(stateFilter);
                }
            }
            else if ("host".equals(entry.getKey())) {
                for (String hostGlob : entry.getValue()) {
                    builder.addHostGlobFilter(hostGlob);
                }
            }
        }
        return builder.buildPredicate();
    }

    private final List<CoordinatorLifecycleState> stateFilters = Lists.newArrayListWithCapacity(6);
    private final List<String> hostGlobs = Lists.newArrayListWithCapacity(6);

    public void addStateFilter(String stateFilter)
    {
        Preconditions.checkNotNull(stateFilter, "stateFilter is null");
        CoordinatorLifecycleState state = CoordinatorLifecycleState.valueOf(stateFilter.toUpperCase());
        Preconditions.checkArgument(state != null, "unknown state " + stateFilter);
        stateFilters.add(state);
    }

    public void addHostGlobFilter(String hostGlob)
    {
        Preconditions.checkNotNull(hostGlob, "hostGlob is null");
        hostGlobs.add(hostGlob);
    }

    public Predicate<CoordinatorStatus> buildPredicate()
    {
        // Filters are evaluated as: set | host | (env & version & type)
        List<Predicate<CoordinatorStatus>> andPredicates = Lists.newArrayListWithCapacity(6);
        if (!stateFilters.isEmpty()) {
            Predicate<CoordinatorStatus> predicate = Predicates.or(Lists.transform(stateFilters, new Function<CoordinatorLifecycleState, StatePredicate>()
            {
                @Override
                public StatePredicate apply(CoordinatorLifecycleState state)
                {
                    return new StatePredicate(state);
                }
            }));
            andPredicates.add(predicate);
        }
        if (!hostGlobs.isEmpty()) {
            Predicate<CoordinatorStatus> predicate = Predicates.or(Lists.transform(hostGlobs, new Function<String, HostPredicate>()
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
        for (CoordinatorLifecycleState stateFilter : stateFilters) {
            uriBuilder.addParameter("state", stateFilter.name());
        }
        return uriBuilder.build();
    }

    public static class HostPredicate implements Predicate<CoordinatorStatus>
    {
        private final UriHostPredicate predicate;

        public HostPredicate(String hostGlob)
        {
            predicate = new UriHostPredicate(hostGlob.toLowerCase());
        }

        @Override
        public boolean apply(@Nullable CoordinatorStatus coordinatorStatus)
        {
            return coordinatorStatus != null &&
                    (predicate.apply(coordinatorStatus.getExternalUri()) || predicate.apply(coordinatorStatus.getInternalUri()));
        }
    }

    public static class StatePredicate implements Predicate<CoordinatorStatus>
    {
        private final CoordinatorLifecycleState state;

        public StatePredicate(CoordinatorLifecycleState state)
        {
            this.state = state;
        }

        @Override
        public boolean apply(@Nullable CoordinatorStatus coordinatorStatus)
        {
            return coordinatorStatus.getState() == state;
        }
    }
}
