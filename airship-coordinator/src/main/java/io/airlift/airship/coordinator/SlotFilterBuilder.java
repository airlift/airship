package io.airlift.airship.coordinator;

import com.google.common.base.Function;
import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.collect.Collections2;
import com.google.common.collect.Lists;
import io.airlift.airship.shared.HttpUriBuilder;
import io.airlift.airship.shared.SlotLifecycleState;
import io.airlift.airship.shared.SlotStatus;

import javax.annotation.Nullable;
import javax.ws.rs.core.UriInfo;
import java.net.URI;
import java.util.Collection;
import java.util.List;
import java.util.Map.Entry;
import java.util.UUID;

import static com.google.common.base.Functions.compose;
import static io.airlift.airship.coordinator.StringFunctions.startsWith;
import static io.airlift.airship.coordinator.StringFunctions.toLowerCase;
import static java.lang.String.format;

public class SlotFilterBuilder
{
    public static SlotFilterBuilder builder() {
        return new SlotFilterBuilder();
    }

    public static Predicate<SlotStatus> build(UriInfo uriInfo, boolean filterRequired, List<UUID> allUuids)
    {
        SlotFilterBuilder builder = new SlotFilterBuilder();
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
            else if ("machine".equals(entry.getKey())) {
                for (String machineGlob : entry.getValue()) {
                    builder.addMachineGlobFilter(machineGlob);
                }
            }
            else if ("uuid".equals(entry.getKey())) {
                for (String shortId : entry.getValue()) {
                    builder.addSlotUuidFilter(shortId);
                }
            }
            else if ("binary".equals(entry.getKey())) {
                for (String binaryGlob : entry.getValue()) {
                    builder.addBinaryGlobFilter(binaryGlob);
                }
            }
            else if ("config".equals(entry.getKey())) {
                for (String configGlob : entry.getValue()) {
                    builder.addConfigGlobFilter(configGlob);
                }
            }
            else if ("all".equals(entry.getKey())) {
                builder.selectAll();
            }
        }
        return builder.buildPredicate(filterRequired, allUuids);
    }

    private final List<SlotLifecycleState> stateFilters = Lists.newArrayListWithCapacity(6);
    private final List<String> slotUuidFilters = Lists.newArrayListWithCapacity(6);
    private final List<String> hostGlobs = Lists.newArrayListWithCapacity(6);
    private final List<String> machineGlobs = Lists.newArrayListWithCapacity(6);
    private final List<String> binaryGlobs = Lists.newArrayListWithCapacity(6);
    private final List<String> configGlobs = Lists.newArrayListWithCapacity(6);
    private boolean selectAll;

    private SlotFilterBuilder()
    {
    }

    public SlotFilterBuilder addStateFilter(String stateFilter)
    {
        Preconditions.checkNotNull(stateFilter, "stateFilter is null");
        SlotLifecycleState state = SlotLifecycleState.lookup(stateFilter);
        Preconditions.checkArgument(state != null, "unknown state " + stateFilter);
        stateFilters.add(state);
        return this;
    }

    public SlotFilterBuilder addSlotUuidFilter(String shortId)
    {
        Preconditions.checkNotNull(shortId, "shortId is null");
        slotUuidFilters.add(shortId);
        return this;
    }

    public SlotFilterBuilder addHostGlobFilter(String hostGlob)
    {
        Preconditions.checkNotNull(hostGlob, "hostGlob is null");
        hostGlobs.add(hostGlob);
        return this;
    }

    public void addMachineGlobFilter(String machineGlob)
    {
        Preconditions.checkNotNull(machineGlob, "machineGlob is null");
        machineGlobs.add(machineGlob);
    }

    public SlotFilterBuilder addBinaryGlobFilter(String binaryGlob)
    {
        Preconditions.checkNotNull(binaryGlob, "binaryGlob is null");
        binaryGlobs.add(binaryGlob);
        return this;
    }

    public SlotFilterBuilder addConfigGlobFilter(String configGlob)
    {
        Preconditions.checkNotNull(configGlob, "configGlob is null");
        configGlobs.add(configGlob);
        return this;
    }

    public SlotFilterBuilder selectAll()
    {
        this.selectAll = true;
        return this;
    }

    public Predicate<SlotStatus> buildPredicate(boolean filterRequired, final List<UUID> allUuids)
    {
        // Filters are evaluated as: set | host | (env & version & type)
        List<Predicate<SlotStatus>> andPredicates = Lists.newArrayListWithCapacity(6);
        if (!slotUuidFilters.isEmpty()) {
            Predicate<SlotStatus> predicate = Predicates.or(Lists.transform(slotUuidFilters, new Function<String, Predicate<SlotStatus>>()
            {
                @Override
                public Predicate<SlotStatus> apply(String shortId)
                {
                    return new SlotUuidPredicate(shortId, allUuids);
                }
            }));
            andPredicates.add(predicate);
        }

        if (!stateFilters.isEmpty()) {
            Predicate<SlotStatus> predicate = Predicates.or(Lists.transform(stateFilters, new Function<SlotLifecycleState, StatePredicate>()
            {
                @Override
                public StatePredicate apply(SlotLifecycleState state)
                {
                    return new StatePredicate(state);
                }
            }));
            andPredicates.add(predicate);
        }

        if (!hostGlobs.isEmpty()) {
            Predicate<SlotStatus> predicate = Predicates.or(Lists.transform(hostGlobs, new Function<String, HostPredicate>()
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
            Predicate<SlotStatus> predicate = Predicates.or(Lists.transform(machineGlobs, new Function<String, MachinePredicate>()
            {
                @Override
                public MachinePredicate apply(String machineGlob)
                {
                    return new MachinePredicate(machineGlob);
                }
            }));
            andPredicates.add(predicate);
        }

        if (!binaryGlobs.isEmpty()) {
            Predicate<SlotStatus> predicate = Predicates.or(Lists.transform(binaryGlobs, new Function<String, BinarySpecPredicate>()
            {
                @Override
                public BinarySpecPredicate apply(String binarySpecPredicate)
                {
                    return new BinarySpecPredicate(binarySpecPredicate);
                }
            }));
            andPredicates.add(predicate);
        }
        if (!configGlobs.isEmpty()) {
            Predicate<SlotStatus> predicate = Predicates.or(Lists.transform(configGlobs, new Function<String, ConfigSpecPredicate>()
            {
                @Override
                public ConfigSpecPredicate apply(String configSpecPredicate)
                {
                    return new ConfigSpecPredicate(configSpecPredicate);
                }
            }));
            andPredicates.add(predicate);
        }

        if (selectAll) {
            return Predicates.alwaysTrue();
        }
        else if (!andPredicates.isEmpty()) {
            return Predicates.and(andPredicates);
        }
        else if (!filterRequired) {
            return Predicates.alwaysTrue();
        }
        else {
            throw new InvalidSlotFilterException();
        }
    }

    public URI buildUri(URI baseUri)
    {
        HttpUriBuilder uriBuilder = HttpUriBuilder.uriBuilderFrom(baseUri);
        return buildUri(uriBuilder);
    }

    public URI buildUri(HttpUriBuilder uriBuilder)
    {
        for (String binaryGlob : binaryGlobs) {
            uriBuilder.addParameter("binary", binaryGlob);
        }
        for (String configGlob : configGlobs) {
            uriBuilder.addParameter("config", configGlob);
        }
        for (String hostGlob : hostGlobs) {
            uriBuilder.addParameter("host", hostGlob);
        }
        for (String machineGlob : machineGlobs) {
            uriBuilder.addParameter("machine", machineGlob);
        }
        for (SlotLifecycleState stateFilter : stateFilters) {
            uriBuilder.addParameter("state", stateFilter.name());
        }
        for (String shortId : slotUuidFilters) {
            uriBuilder.addParameter("uuid", shortId);
        }
        if (selectAll) {
            uriBuilder.addParameter("all");
        }
        return uriBuilder.build();
    }

    public static class SlotUuidPredicate implements Predicate<SlotStatus>
    {
        private final UUID uuid;

        public SlotUuidPredicate(String shortId, List<UUID> allUuids)
        {
            Predicate<UUID> startsWithPrefix = Predicates.compose(startsWith(shortId.toLowerCase()), compose(toLowerCase(), StringFunctions.<UUID>toStringFunction()));
            Collection<UUID> matches = Collections2.filter(allUuids, startsWithPrefix);

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

        public SlotUuidPredicate(UUID uuid)
        {
            this.uuid = uuid;
        }

        @Override
        public boolean apply(@Nullable SlotStatus slotStatus)
        {
            return slotStatus != null &&
                    slotStatus.getId() != null &&
                    uuid != null &&
                    uuid.equals(slotStatus.getId());
        }
    }

    public static class HostPredicate implements Predicate<SlotStatus>
    {
        private final UriHostPredicate predicate;

        public HostPredicate(String hostGlob)
        {
            predicate = new UriHostPredicate(hostGlob.toLowerCase());
        }

        @Override
        public boolean apply(@Nullable SlotStatus slotStatus)
        {
            return slotStatus != null &&
                    (predicate.apply(slotStatus.getExternalUri()) || predicate.apply(slotStatus.getSelf()));
        }
    }

    public static class MachinePredicate implements Predicate<SlotStatus>
    {
        private final GlobPredicate predicate;

        public MachinePredicate(String machineGlob)
        {
            predicate = new GlobPredicate(machineGlob);
        }

        @Override
        public boolean apply(@Nullable SlotStatus slotStatus)
        {
            return slotStatus != null && predicate.apply(slotStatus.getInstanceId());
        }
    }

    public static class StatePredicate implements Predicate<SlotStatus>
    {
        private final SlotLifecycleState state;

        public StatePredicate(SlotLifecycleState state)
        {
            this.state = state;
        }

        @Override
        public boolean apply(SlotStatus slotStatus)
        {
            return slotStatus.getState() == state;
        }
    }

    public static class BinarySpecPredicate implements Predicate<SlotStatus>
    {
        private final Predicate<CharSequence> glob;

        public BinarySpecPredicate(String binaryFilter)
        {
            glob = new GlobPredicate("*" + binaryFilter + "*");
        }

        @Override
        public boolean apply(@Nullable SlotStatus slotStatus)
        {
            return (slotStatus != null) &&
                    (slotStatus.getAssignment() != null) &&
                    glob.apply(slotStatus.getAssignment().getBinary());
        }
    }

    public static class ConfigSpecPredicate implements Predicate<SlotStatus>
    {
        private final Predicate<CharSequence> glob;

        public ConfigSpecPredicate(String configFilter)
        {
            glob = new GlobPredicate("*" + configFilter + "*");
        }

        @Override
        public boolean apply(@Nullable SlotStatus slotStatus)
        {
            return (slotStatus != null) &&
                    (slotStatus.getAssignment() != null) &&
                    glob.apply(slotStatus.getAssignment().getConfig());
        }
    }
}
