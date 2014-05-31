package io.airlift.airship.coordinator;

import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.collect.Lists;
import io.airlift.airship.shared.CoordinatorLifecycleState;
import io.airlift.airship.shared.CoordinatorStatus;
import io.airlift.airship.shared.HttpUriBuilder;

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
            if ("uuid" .equals(entry.getKey())) {
                for (String uuidFilter : entry.getValue()) {
                    builder.addUuidFilter(uuidFilter);
                }
            }
            if ("!uuid" .equals(entry.getKey())) {
                for (String notUuidFilter : entry.getValue()) {
                    builder.addNotUuidFilter(notUuidFilter);
                }
            }
            else if ("state".equals(entry.getKey())) {
                for (String stateFilter : entry.getValue()) {
                    builder.addStateFilter(stateFilter);
                }
            }
            else if ("!state".equals(entry.getKey())) {
                for (String notStateFilter : entry.getValue()) {
                    builder.addNotStateFilter(notStateFilter);
                }
            }
            else if ("host".equals(entry.getKey())) {
                for (String hostGlob : entry.getValue()) {
                    builder.addHostGlobFilter(hostGlob);
                }
            }
            else if ("!host".equals(entry.getKey())) {
                for (String notHostGlob : entry.getValue()) {
                    builder.addNotHostGlobFilter(notHostGlob);
                }
            }
            else if ("machine".equals(entry.getKey())) {
                for (String machineGlob : entry.getValue()) {
                    builder.addMachineGlobFilter(machineGlob);
                }
            }
            else if ("!machine".equals(entry.getKey())) {
                for (String notMachineGlob : entry.getValue()) {
                    builder.addNotMachineGlobFilter(notMachineGlob);
                }
            }
            else if ("all".equals(entry.getKey())) {
                builder.selectAll();
            }
        }
        return builder.buildPredicate();
    }

    private final List<String> uuidFilters = Lists.newArrayListWithCapacity(6);
    private final List<String> notUuidFilters = Lists.newArrayListWithCapacity(6);
    private final List<CoordinatorLifecycleState> stateFilters = Lists.newArrayListWithCapacity(6);
    private final List<CoordinatorLifecycleState> notStateFilters = Lists.newArrayListWithCapacity(6);
    private final List<String> hostGlobs = Lists.newArrayListWithCapacity(6);
    private final List<String> notHostGlobs = Lists.newArrayListWithCapacity(6);
    private final List<String> machineGlobs = Lists.newArrayListWithCapacity(6);
    private final List<String> notMachineGlobs = Lists.newArrayListWithCapacity(6);
    private boolean selectAll;

    public void addUuidFilter(String uuid)
    {
        Preconditions.checkNotNull(uuid, "uuid is null");
        uuidFilters.add(uuid);
    }

    public void addNotUuidFilter(String notUuid)
    {
        Preconditions.checkNotNull(notUuid, "notUuid is null");
        notUuidFilters.add(notUuid);
    }

    public void addStateFilter(String stateFilter)
    {
        Preconditions.checkNotNull(stateFilter, "stateFilter is null");
        CoordinatorLifecycleState state = CoordinatorLifecycleState.valueOf(stateFilter.toUpperCase());
        Preconditions.checkArgument(state != null, "unknown state %s", stateFilter);
        stateFilters.add(state);
    }

    public void addNotStateFilter(String notStateFilter)
    {
        Preconditions.checkNotNull(notStateFilter, "notStateFilter is null");
        CoordinatorLifecycleState state = CoordinatorLifecycleState.valueOf(notStateFilter.toUpperCase());
        Preconditions.checkArgument(state != null, "unknown state %s", notStateFilter);
        notStateFilters.add(state);
    }

    public void addHostGlobFilter(String hostGlob)
    {
        Preconditions.checkNotNull(hostGlob, "hostGlob is null");
        hostGlobs.add(hostGlob);
    }

    public void addNotHostGlobFilter(String notHostGlob)
    {
        Preconditions.checkNotNull(notHostGlob, "notHostGlob is null");
        notHostGlobs.add(notHostGlob);
    }

    public void addMachineGlobFilter(String machineGlob)
    {
        Preconditions.checkNotNull(machineGlob, "machineGlob is null");
        machineGlobs.add(machineGlob);
    }

    public void addNotMachineGlobFilter(String notMachineGlob)
    {
        Preconditions.checkNotNull(notMachineGlob, "notMachineGlob is null");
        notMachineGlobs.add(notMachineGlob);
    }

    public void selectAll()
    {
        this.selectAll = true;
    }

    public Predicate<CoordinatorStatus> buildPredicate()
    {
        Predicate<CoordinatorStatus> include = buildIncludesPredicate();

        Optional<Predicate<CoordinatorStatus>> excludesPredicate = buildExcludesPredicate();
        if (excludesPredicate.isPresent()) {
            // includes and not excluded
            return Predicates.and(include, Predicates.not(excludesPredicate.get()));
        }

        return include;
    }

    private Predicate<CoordinatorStatus> buildIncludesPredicate()
    {
        List<Predicate<CoordinatorStatus>> andPredicates = Lists.newArrayListWithCapacity(6);
        if (!uuidFilters.isEmpty()) {
            Predicate<CoordinatorStatus> predicate = Predicates.or(Lists.transform(uuidFilters, new Function<String, UuidPredicate>()
            {
                @Override
                public UuidPredicate apply(String uuid)
                {
                    return new UuidPredicate(uuid);
                }
            }));
            andPredicates.add(predicate);
        }
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
        if (!machineGlobs.isEmpty()) {
            Predicate<CoordinatorStatus> predicate = Predicates.or(Lists.transform(machineGlobs, new Function<String, MachinePredicate>()
            {
                @Override
                public MachinePredicate apply(String machineGlob)
                {
                    return new MachinePredicate(machineGlob);
                }
            }));
            andPredicates.add(predicate);
        }

        if (selectAll || andPredicates.isEmpty()) {
            return Predicates.alwaysTrue();
        }
        else {
            return Predicates.and(andPredicates);
        }
    }

    private Optional<Predicate<CoordinatorStatus>> buildExcludesPredicate()
    {
        List<Predicate<CoordinatorStatus>> excludes = Lists.newArrayListWithCapacity(6);
        excludes.addAll(Lists.transform(notUuidFilters, new Function<String, UuidPredicate>()
        {
            @Override
            public UuidPredicate apply(String uuid)
            {
                return new UuidPredicate(uuid);
            }
        }));
        excludes.addAll(Lists.transform(notStateFilters, new Function<CoordinatorLifecycleState, StatePredicate>()
        {
            @Override
            public StatePredicate apply(CoordinatorLifecycleState state)
            {
                return new StatePredicate(state);
            }
        }));
        excludes.addAll(Lists.transform(notHostGlobs, new Function<String, HostPredicate>()
        {
            @Override
            public HostPredicate apply(String hostGlob)
            {
                return new HostPredicate(hostGlob);
            }
        }));
        excludes.addAll(Lists.transform(notMachineGlobs, new Function<String, MachinePredicate>()
        {
            @Override
            public MachinePredicate apply(String machineGlob)
            {
                return new MachinePredicate(machineGlob);
            }
        }));

        if (excludes.isEmpty()) {
            return Optional.absent();
        }
        return Optional.of(Predicates.or(excludes));
    }

    public URI buildUri(URI baseUri)
    {
        HttpUriBuilder uriBuilder = HttpUriBuilder.uriBuilderFrom(baseUri);
        return buildUri(uriBuilder);
    }

    public URI buildUri(HttpUriBuilder uriBuilder)
    {
        for (String uuidFilter : uuidFilters) {
            uriBuilder.addParameter("uuid", uuidFilter);
        }
        for (String notUuidFilter : notUuidFilters) {
            uriBuilder.addParameter("!uuid", notUuidFilter);
        }
        for (String hostGlob : hostGlobs) {
            uriBuilder.addParameter("host", hostGlob);
        }
        for (String notHostGlob : notHostGlobs) {
            uriBuilder.addParameter("!host", notHostGlob);
        }
        for (String machineGlob : machineGlobs) {
            uriBuilder.addParameter("machine", machineGlob);
        }
        for (String notMachineGlob : notMachineGlobs) {
            uriBuilder.addParameter("!machine", notMachineGlob);
        }
        for (CoordinatorLifecycleState stateFilter : stateFilters) {
            uriBuilder.addParameter("state", stateFilter.name());
        }
        for (CoordinatorLifecycleState notStateFilter : notStateFilters) {
            uriBuilder.addParameter("!state", notStateFilter.name());
        }
        if (selectAll) {
            uriBuilder.addParameter("all");
        }
        return uriBuilder.build();
    }

    public static class UuidPredicate implements Predicate<CoordinatorStatus>
    {
        private final String uuid;

        public UuidPredicate(String uuid)
        {
            this.uuid = uuid;
        }

        @Override
        public boolean apply(@Nullable CoordinatorStatus coordinatorStatus)
        {
            return uuid.equals(coordinatorStatus.getCoordinatorId());
        }
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

    public static class MachinePredicate implements Predicate<CoordinatorStatus>
    {
        private final GlobPredicate predicate;

        public MachinePredicate(String machineGlob)
        {
            predicate = new GlobPredicate(machineGlob);
        }

        @Override
        public boolean apply(@Nullable CoordinatorStatus coordinatorStatus)
        {
            return coordinatorStatus != null && predicate.apply(coordinatorStatus.getInstanceId());
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
