package io.airlift.airship.coordinator;

import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.base.Splitter;
import com.google.common.collect.Collections2;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import io.airlift.airship.shared.AgentLifecycleState;
import io.airlift.airship.shared.AgentStatus;
import io.airlift.airship.shared.Assignment;
import io.airlift.airship.shared.HttpUriBuilder;
import io.airlift.airship.shared.Installation;
import io.airlift.airship.shared.InstallationUtils;
import io.airlift.airship.shared.Repository;
import io.airlift.airship.shared.SlotStatus;

import javax.annotation.Nullable;
import javax.ws.rs.core.UriInfo;
import java.net.URI;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.UUID;

import static com.google.common.base.Predicates.notNull;
import static io.airlift.airship.coordinator.StringFunctions.startsWith;
import static io.airlift.airship.coordinator.StringFunctions.toLowerCase;
import static io.airlift.airship.shared.AgentLifecycleState.ONLINE;
import static io.airlift.airship.shared.InstallationUtils.getAvailableResources;
import static io.airlift.airship.shared.InstallationUtils.resourcesAreAvailable;
import static io.airlift.airship.shared.InstallationUtils.toInstallation;
import static java.lang.String.format;

public class AgentFilterBuilder
{
    public static AgentFilterBuilder builder()
    {
        return new AgentFilterBuilder();
    }

    public static Predicate<AgentStatus> build(UriInfo uriInfo, List<String> allAgentUuids, List<UUID> allSlotUuids)
    {
        return build(uriInfo, allAgentUuids, allSlotUuids, false, null);
    }

    public static Predicate<AgentStatus> build(UriInfo uriInfo,
            List<String> allAgentUuids,
            List<UUID> allSlotUuids,
            boolean allowDuplicateInstallationsOnAnAgent,
            Repository repository)
    {
        AgentFilterBuilder builder = new AgentFilterBuilder();
        for (Entry<String, List<String>> entry : uriInfo.getQueryParameters().entrySet()) {
            if ("uuid".equals(entry.getKey())) {
                for (String uuidFilter : entry.getValue()) {
                    builder.addUuidFilter(uuidFilter);
                }
            }
            if ("!uuid".equals(entry.getKey())) {
                for (String notUuidFilter : entry.getValue()) {
                    builder.addNotUuidFilter(notUuidFilter);
                }
            }
            if ("state".equals(entry.getKey())) {
                for (String stateFilter : entry.getValue()) {
                    builder.addStateFilter(stateFilter);
                }
            }
            if ("!state".equals(entry.getKey())) {
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
            else if ("slotUuid".equals(entry.getKey())) {
                for (String uuidGlob : entry.getValue()) {
                    builder.addSlotUuidGlobFilter(uuidGlob);
                }
            }
            else if ("!slotUuid".equals(entry.getKey())) {
                for (String notUuidGlob : entry.getValue()) {
                    builder.addNotSlotUuidGlobFilter(notUuidGlob);
                }
            }
            else if ("assignable".equals(entry.getKey())) {
                Preconditions.checkArgument(repository != null, "repository is null");
                for (String assignment : entry.getValue()) {
                    List<String> split = ImmutableList.copyOf(Splitter.on("@").limit(2).split(assignment));
                    Preconditions.checkArgument(split.size() == 2, "Invalid canInstall filter %s", assignment);
                    builder.addAssignableFilter(new Assignment(split.get(0), "@" + split.get(1)));
                }
            }
            else if ("all".equals(entry.getKey())) {
                builder.selectAll();
            }
        }
        return builder.build(allAgentUuids, allSlotUuids, allowDuplicateInstallationsOnAnAgent, repository);
    }

    private final List<String> uuidFilters = Lists.newArrayListWithCapacity(6);
    private final List<String> notUuidFilters = Lists.newArrayListWithCapacity(6);
    private final List<AgentLifecycleState> stateFilters = Lists.newArrayListWithCapacity(6);
    private final List<AgentLifecycleState> notStateFilters = Lists.newArrayListWithCapacity(6);
    private final List<String> slotUuidGlobs = Lists.newArrayListWithCapacity(6);
    private final List<String> notSlotUuidGlobs = Lists.newArrayListWithCapacity(6);
    private final List<String> hostGlobs = Lists.newArrayListWithCapacity(6);
    private final List<String> notHostGlobs = Lists.newArrayListWithCapacity(6);
    private final List<String> machineGlobs = Lists.newArrayListWithCapacity(6);
    private final List<String> notMachineGlobs = Lists.newArrayListWithCapacity(6);
    private final List<Assignment> assignableFilters = Lists.newArrayListWithCapacity(6);
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
        AgentLifecycleState state = AgentLifecycleState.valueOf(stateFilter.toUpperCase());
        Preconditions.checkArgument(state != null, "unknown state %s", stateFilter);
        stateFilters.add(state);
    }

    public void addNotStateFilter(String notStateFilter)
    {
        Preconditions.checkNotNull(notStateFilter, "notStateFilter is null");
        AgentLifecycleState state = AgentLifecycleState.valueOf(notStateFilter.toUpperCase());
        Preconditions.checkArgument(state != null, "unknown state %s", notStateFilter);
        notStateFilters.add(state);
    }

    public void addSlotUuidGlobFilter(String slotUuidGlob)
    {
        Preconditions.checkNotNull(slotUuidGlob, "slotUuidGlob is null");
        slotUuidGlobs.add(slotUuidGlob);
    }

    public void addNotSlotUuidGlobFilter(String notSlotUuidGlob)
    {
        Preconditions.checkNotNull(notSlotUuidGlob, "notSlotUuidGlob is null");
        notSlotUuidGlobs.add(notSlotUuidGlob);
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

    public void addAssignableFilter(Assignment assignment)
    {
        Preconditions.checkNotNull(assignment, "assignment is null");
        assignableFilters.add(assignment);
    }

    public void selectAll()
    {
        this.selectAll = true;
    }

    public Predicate<AgentStatus> build(final List<String> allAgentUuids,
            final List<UUID> allSlotUuids,
            final boolean allowDuplicateInstallationsOnAnAgent,
            final Repository repository)
    {
        Predicate<AgentStatus> include = buildIncludesPredicate(allAgentUuids, allSlotUuids, allowDuplicateInstallationsOnAnAgent, repository);

        Optional<Predicate<AgentStatus>> excludesPredicate = buildExcludesPredicate(allAgentUuids, allSlotUuids);
        if (excludesPredicate.isPresent()) {
            // includes and not excluded
            return Predicates.and(include, Predicates.not(excludesPredicate.get()));
        }

        return include;
    }

    private Predicate<AgentStatus> buildIncludesPredicate(final List<String> allAgentUuids,
            final List<UUID> allSlotUuids,
            final boolean allowDuplicateInstallationsOnAnAgent,
            final Repository repository)
    {
        List<Predicate<AgentStatus>> andPredicates = Lists.newArrayListWithCapacity(6);
        if (!uuidFilters.isEmpty()) {
            Predicate<AgentStatus> predicate = Predicates.or(Lists.transform(uuidFilters, new Function<String, UuidPredicate>()
            {
                @Override
                public UuidPredicate apply(String uuid)
                {
                    return new UuidPredicate(uuid, allAgentUuids);
                }
            }));
            andPredicates.add(predicate);
        }
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
                    return new SlotUuidPredicate(slotUuidGlob, allSlotUuids);
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
        if (!machineGlobs.isEmpty()) {
            Predicate<AgentStatus> predicate = Predicates.or(Lists.transform(machineGlobs, new Function<String, MachinePredicate>()
            {
                @Override
                public MachinePredicate apply(String machineGlob)
                {
                    return new MachinePredicate(machineGlob);
                }
            }));
            andPredicates.add(predicate);
        }
        if (!assignableFilters.isEmpty()) {
            Predicate<AgentStatus> predicate = Predicates.or(Lists.transform(assignableFilters, new Function<Assignment, AssignablePredicate>()
            {
                @Override
                public AssignablePredicate apply(Assignment assignment)
                {
                    return new AssignablePredicate(assignment, allowDuplicateInstallationsOnAnAgent, repository);
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

    private Optional<Predicate<AgentStatus>> buildExcludesPredicate(final List<String> allAgentUuids, final List<UUID> allSlotUuids)
    {
        List<Predicate<AgentStatus>> excludes = Lists.newArrayListWithCapacity(6);
        excludes.addAll(Lists.transform(notUuidFilters, new Function<String, UuidPredicate>()
        {
            @Override
            public UuidPredicate apply(String uuid)
            {
                return new UuidPredicate(uuid, allAgentUuids);
            }
        }));
        excludes.addAll(Lists.transform(notStateFilters, new Function<AgentLifecycleState, StatePredicate>()
        {
            @Override
            public StatePredicate apply(AgentLifecycleState state)
            {
                return new StatePredicate(state);
            }
        }));
        excludes.addAll(Lists.transform(notSlotUuidGlobs, new Function<String, SlotUuidPredicate>()
        {
            @Override
            public SlotUuidPredicate apply(String slotUuidGlob)
            {
                return new SlotUuidPredicate(slotUuidGlob, allSlotUuids);
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
        for (AgentLifecycleState stateFilter : stateFilters) {
            uriBuilder.addParameter("state", stateFilter.name());
        }
        for (AgentLifecycleState notStateFilter : notStateFilters) {
            uriBuilder.addParameter("!state", notStateFilter.name());
        }
        for (String shortId : slotUuidGlobs) {
            uriBuilder.addParameter("slotUuid", shortId);
        }
        for (String notShortId : notSlotUuidGlobs) {
            uriBuilder.addParameter("!slotUuid", notShortId);
        }
        for (Assignment assignment : assignableFilters) {
            uriBuilder.addParameter("assignable", assignment.getBinary() + assignment.getConfig());
        }
        if (selectAll) {
            uriBuilder.addParameter("all");
        }
        return uriBuilder.build();
    }

    public static class UuidPredicate
            implements Predicate<AgentStatus>
    {
        private final String uuid;

        public UuidPredicate(String shortId, List<String> allUuids)
        {
            allUuids = ImmutableList.copyOf(Iterables.filter(allUuids, notNull()));
            Predicate<String> startsWithPrefix = Predicates.compose(startsWith(shortId.toLowerCase()), toLowerCase());
            Collection<String> matches = Collections2.filter(allUuids, startsWithPrefix);

            if (matches.size() > 1) {
                throw new IllegalArgumentException(format("Ambiguous expansion for id '%s': %s", shortId, matches));
            }

            if (matches.isEmpty()) {
                uuid = null;
            }
            else {
                uuid = matches.iterator().next();
            }
        }

        @Override
        public boolean apply(@Nullable AgentStatus agentStatus)
        {
            return agentStatus != null &&
                    agentStatus.getAgentId() != null &&
                    uuid != null &&
                    uuid.equals(agentStatus.getAgentId());
        }
    }

    public static class SlotUuidPredicate
            implements Predicate<AgentStatus>
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
        public boolean apply(AgentStatus agentStatus)
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

    public static class HostPredicate
            implements Predicate<AgentStatus>
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

    public static class MachinePredicate
            implements Predicate<AgentStatus>
    {
        private final GlobPredicate predicate;

        public MachinePredicate(String machineGlob)
        {
            predicate = new GlobPredicate(machineGlob);
        }

        @Override
        public boolean apply(@Nullable AgentStatus agentStatus)
        {
            return agentStatus != null && predicate.apply(agentStatus.getInstanceId());
        }
    }

    public static class StatePredicate
            implements Predicate<AgentStatus>
    {
        private final AgentLifecycleState state;

        public StatePredicate(AgentLifecycleState state)
        {
            this.state = state;
        }

        @Override
        public boolean apply(@Nullable AgentStatus agentStatus)
        {
            return agentStatus != null && agentStatus.getState() == state;
        }
    }

    public static class AssignablePredicate
            implements Predicate<AgentStatus>
    {
        private final Assignment assignment;
        private final boolean allowDuplicateInstallationsOnAnAgent;
        private final Repository repository;

        public AssignablePredicate(Assignment assignment, boolean allowDuplicateInstallationsOnAnAgent, Repository repository)
        {
            this.assignment = InstallationUtils.resolveAssignment(repository, assignment);
            this.allowDuplicateInstallationsOnAnAgent = allowDuplicateInstallationsOnAnAgent;
            this.repository = repository;
        }

        @Override
        public boolean apply(@Nullable AgentStatus status)
        {
            // We can only install on online agents
            if (status.getState() != ONLINE) {
                return false;
            }

            // Constraints: normally we only allow only instance of a binary+config on each agent
            if (!allowDuplicateInstallationsOnAnAgent) {
                for (SlotStatus slot : status.getSlotStatuses()) {
                    if (repository.binaryEqualsIgnoreVersion(assignment.getBinary(), slot.getAssignment().getBinary()) &&
                            repository.configEqualsIgnoreVersion(assignment.getConfig(), slot.getAssignment().getConfig())) {
                        return false;
                    }
                }
            }

            // agents without declared resources are considered to have unlimited resources
            if (!status.getResources().isEmpty()) {
                // verify that required resources are available
                Installation installation = toInstallation(repository, assignment);
                Map<String, Integer> availableResources = getAvailableResources(status);
                if (!resourcesAreAvailable(availableResources, installation.getResources())) {
                    return false;
                }
            }

            return true;
        }
    }
}
