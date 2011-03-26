package com.proofpoint.galaxy.console;

import com.google.common.base.Function;
import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.collect.Lists;
import com.google.common.net.InetAddresses;
import com.proofpoint.galaxy.BinarySpec;
import com.proofpoint.galaxy.ConfigSpec;
import com.proofpoint.galaxy.LifecycleState;
import com.proofpoint.galaxy.Slot;
import com.proofpoint.galaxy.SlotStatus;

import javax.annotation.Nullable;
import javax.ws.rs.core.UriInfo;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Map.Entry;
import java.util.regex.Pattern;

public class SlotFilterBuilder
{
    public static Predicate<Slot> build(UriInfo uriInfo)
    {
        SlotFilterBuilder builder = new SlotFilterBuilder();
        for (Entry<String, List<String>> entry : uriInfo.getQueryParameters().entrySet()) {
            if ("state" .equals(entry.getKey())) {
                for (String stateFilter : entry.getValue()) {
                    builder.addStateFilter(stateFilter);
                }
            }
            else if ("set" .equals(entry.getKey())) {
                for (String setFilter : entry.getValue()) {
                    builder.addSetFilter(setFilter);
                }
            }
            else if ("host" .equals(entry.getKey())) {
                for (String hostGlob : entry.getValue()) {
                    builder.addHostGlobFilter(hostGlob);
                }
            }
            else if ("ip" .equals(entry.getKey())) {
                for (String ipFilter : entry.getValue()) {
                    builder.addIpFilter(ipFilter);
                }
            }
            else if ("binary" .equals(entry.getKey())) {
                for (String binaryGlob : entry.getValue()) {
                    builder.addBinaryGlobFilter(binaryGlob);
                }
            }
            else if ("config" .equals(entry.getKey())) {
                for (String configGlob : entry.getValue()) {
                    builder.addConfigGlobFilter(configGlob);
                }
            }
        }
        return builder.build();
    }

    private final List<StatePredicate> stateFilters = Lists.newArrayListWithCapacity(6);
    private final List<SetPredicate> setFilters = Lists.newArrayListWithCapacity(6);
    private final List<HostPredicate> hostFilters = Lists.newArrayListWithCapacity(6);
    private final List<IpPredicate> ipFilters = Lists.newArrayListWithCapacity(6);
    private final List<BinarySpecPredicate> binarySpecPredicates = Lists.newArrayListWithCapacity(6);
    private final List<ConfigSpecPredicate> configSpecPredicates = Lists.newArrayListWithCapacity(6);

    public void addStateFilter(String stateFilter)
    {
        Preconditions.checkNotNull(stateFilter, "stateFilter is null");
        LifecycleState state = LifecycleState.lookup(stateFilter);
        Preconditions.checkArgument(state != null, "unknown state " + stateFilter);
        stateFilters.add(new StatePredicate(state));
    }

    public void addSetFilter(String setFilter)
    {
        Preconditions.checkNotNull(setFilter, "setFilter is null");
        SlotSet set = SlotSet.lookup(setFilter);
        Preconditions.checkArgument(set != null, "unknown set " + setFilter);
        setFilters.add(new SetPredicate(set));
    }

    public void addHostGlobFilter(String hostGlob)
    {
        Preconditions.checkNotNull(hostGlob, "hostGlob is null");
        hostFilters.add(new HostPredicate(hostGlob));
    }

    public void addIpFilter(String ipFilter)
    {
        Preconditions.checkNotNull(ipFilter, "ipFilter is null");
        ipFilters.add(new IpPredicate(ipFilter));
    }

    public void addBinaryGlobFilter(String binaryGlob)
    {
        Preconditions.checkNotNull(binaryGlob, "binaryGlob is null");
        binarySpecPredicates.add(new BinarySpecPredicate(binaryGlob));
    }

    public void addConfigGlobFilter(String configGlob)
    {
        Preconditions.checkNotNull(configGlob, "configGlob is null");
        configSpecPredicates.add(new ConfigSpecPredicate(configGlob));
    }

    public Predicate<Slot> build()
    {
        return Predicates.compose(buildStatusFilter(), new Function<Slot, SlotStatus>()
        {
            @Override
            public SlotStatus apply(Slot slot)
            {
                return slot.status();
            }
        });
    }

    public Predicate<SlotStatus> buildStatusFilter()
    {
        // Filters are evaluated as: set | host | (env & version & type)
        List<Predicate<SlotStatus>> orPredicates = Lists.newArrayListWithCapacity(6);
        if (!stateFilters.isEmpty()) {
            Predicate<SlotStatus> predicate = Predicates.and(stateFilters);
            orPredicates.add(predicate);
        }
        if (!setFilters.isEmpty()) {
            Predicate<SlotStatus> predicate = Predicates.and(setFilters);
            orPredicates.add(predicate);
        }
        if (!hostFilters.isEmpty()) {
            Predicate<SlotStatus> predicate = Predicates.and(hostFilters);
            orPredicates.add(predicate);
        }
        if (!ipFilters.isEmpty()) {
            Predicate<SlotStatus> predicate = Predicates.and(ipFilters);
            orPredicates.add(predicate);
        }
        if (!binarySpecPredicates.isEmpty() || !configSpecPredicates.isEmpty()) {
            Predicate<SlotStatus> predicate = Predicates.and(
                    Predicates.and(binarySpecPredicates),
                    Predicates.and(configSpecPredicates));
            orPredicates.add(predicate);
        }
        if (!orPredicates.isEmpty()) {
            return Predicates.or(orPredicates);
        }
        else {
            return Predicates.alwaysTrue();
        }
    }

    public static class HostPredicate implements Predicate<SlotStatus>
    {
        private final Predicate<CharSequence> predicate;

        public HostPredicate(String hostGlob)
        {
            predicate = new GlobPredicate(hostGlob.toLowerCase());
        }

        @Override
        public boolean apply(@Nullable SlotStatus slotStatus)
        {
            return slotStatus != null &&
                    slotStatus.getSelf() != null &&
                    slotStatus.getSelf().getHost() != null &&
                    predicate.apply(slotStatus.getSelf().getHost().toLowerCase());
        }
    }

    public static class IpPredicate implements Predicate<SlotStatus>
    {
        private final Predicate<InetAddress> predicate;

        public IpPredicate(String ipFilter)
        {
            predicate = Predicates.equalTo(InetAddresses.forString(ipFilter));
        }

        @Override
        public boolean apply(@Nullable SlotStatus slotStatus)
        {
            try {
                return slotStatus != null &&
                        slotStatus.getSelf() != null &&
                        slotStatus.getSelf().getHost() != null &&
                        predicate.apply(InetAddress.getByName(slotStatus.getSelf().getHost()));
            }
            catch (UnknownHostException e) {
                return false;
            }
        }
    }

    public static class StatePredicate implements Predicate<SlotStatus>
    {
        private final LifecycleState state;

        public StatePredicate(LifecycleState state)
        {
            this.state = state;
        }

        @Override
        public boolean apply(@Nullable SlotStatus slotStatus)
        {
            return slotStatus.getState() == state;
        }
    }

    public static class SetPredicate implements Predicate<SlotStatus>
    {
        private final SlotSet state;

        public SetPredicate(SlotSet slotStatus)
        {
            this.state = slotStatus;
        }

        @Override
        public boolean apply(@Nullable SlotStatus slotStatus)
        {
            if (slotStatus == null) {
                return false;
            }
            switch (state) {
                case EMPTY:
                    return slotStatus.getState() == LifecycleState.UNASSIGNED;
                case TAKEN:
                    return slotStatus.getState() != LifecycleState.UNASSIGNED;
                case ALL:
                    return true;
            }
            // this will never happen
            throw new AssertionError();
        }
    }

    public static class BinarySpecPredicate implements Predicate<SlotStatus>
    {

        private final Predicate<CharSequence> groupIdGlob;
        private final Predicate<CharSequence> artifactIdGlob;
        private final Predicate<CharSequence> packingGlob;
        private final Predicate<CharSequence> classifierGlob;
        private final Predicate<CharSequence> versionGlob;

        public BinarySpecPredicate(String binaryFilter)
        {
            BinarySpec binarySpec = BinarySpec.valueOf(binaryFilter);
            groupIdGlob = new GlobPredicate(binarySpec.getGroupId());
            artifactIdGlob = new GlobPredicate(binarySpec.getArtifactId());
            packingGlob = new GlobPredicate(binarySpec.getPackaging());
            if (binarySpec.getClassifier() != null) {
                classifierGlob = new GlobPredicate(binarySpec.getClassifier());
            }
            else {
                classifierGlob = Predicates.isNull();
            }
            versionGlob = new GlobPredicate(binarySpec.getVersion());

        }

        @Override
        public boolean apply(@Nullable SlotStatus slotStatus)
        {
            if (slotStatus == null) {
                return false;
            }
            BinarySpec binary = slotStatus.getBinary();
            return binary != null &&
                    groupIdGlob.apply(binary.getGroupId()) &&
                    artifactIdGlob.apply(binary.getArtifactId()) &&
                    packingGlob.apply(binary.getPackaging()) &&
                    classifierGlob.apply(binary.getClassifier()) &&
                    versionGlob.apply(binary.getVersion());

        }
    }

    public static class ConfigSpecPredicate implements Predicate<SlotStatus>
    {

        private final Predicate<CharSequence> componentGlob;
        private final Predicate<CharSequence> environmentGlob;
        private final Predicate<CharSequence> poolGlob;
        private final Predicate<CharSequence> versionGlob;

        public ConfigSpecPredicate(String configFilter)
        {
            ConfigSpec configSpec = ConfigSpec.valueOf(configFilter);
            componentGlob = new GlobPredicate(configSpec.getComponent());
            environmentGlob = new GlobPredicate(configSpec.getEnvironment());
            if (configSpec.getPool() != null) {
                poolGlob = new GlobPredicate(configSpec.getPool());
            }
            else {
                poolGlob = Predicates.isNull();
            }
            versionGlob = new GlobPredicate(configSpec.getVersion());

        }

        @Override
        public boolean apply(@Nullable SlotStatus slotStatus)
        {
            if (slotStatus == null) {
                return false;
            }
            ConfigSpec config = slotStatus.getConfig();
            return config != null &&
                    componentGlob.apply(config.getComponent()) &&
                    environmentGlob.apply(config.getEnvironment()) &&
                    poolGlob.apply(config.getPool()) &&
                    versionGlob.apply(config.getVersion());

        }
    }

    public static class GlobPredicate extends RegexPredicate
    {
        public GlobPredicate(String glob)
        {
            super(globToPattern(glob));
        }
    }

    public static class RegexPredicate implements Predicate<CharSequence>
    {
        private final Pattern pattern;

        public RegexPredicate(Pattern pattern)
        {
            this.pattern = pattern;
        }

        public boolean apply(@Nullable CharSequence input)
        {
            return input != null && pattern.matcher(input).matches();
        }
    }

    private static Pattern globToPattern(String glob)
    {
        glob = glob.trim();
        StringBuilder regex = new StringBuilder(glob.length() * 2);

        boolean escaped = false;
        int curlyDepth = 0;
        for (char currentChar : glob.toCharArray()) {
            switch (currentChar) {
                case '*':
                    if (escaped) {
                        regex.append("\\*");
                    }
                    else {
                        regex.append(".*");
                    }
                    escaped = false;
                    break;
                case '?':
                    if (escaped) {
                        regex.append("\\?");
                    }
                    else {
                        regex.append('.');
                    }
                    escaped = false;
                    break;
                case '.':
                case '(':
                case ')':
                case '+':
                case '|':
                case '^':
                case '$':
                    regex.append('\\');
                    regex.append(currentChar);
                    escaped = false;
                    break;
                case '\\':
                    if (escaped) {
                        regex.append("\\\\");
                        escaped = false;
                    }
                    else {
                        escaped = true;
                    }
                    break;
                case '{':
                    if (escaped) {
                        regex.append("\\{");
                    }
                    else {
                        regex.append('(');
                        curlyDepth++;
                    }
                    escaped = false;
                    break;
                case '}':
                    if (curlyDepth > 0 && !escaped) {
                        regex.append(')');
                        curlyDepth--;
                    }
                    else if (escaped) {
                        regex.append("\\}");
                    }
                    else {
                        regex.append("}");
                    }
                    escaped = false;
                    break;
                case ',':
                    if (curlyDepth > 0 && !escaped) {
                        regex.append('|');
                    }
                    else if (escaped) {
                        regex.append("\\,");
                    }
                    else {
                        regex.append(",");
                    }
                    break;
                default:
                    escaped = false;
                    regex.append(currentChar);
            }
        }
        return Pattern.compile(regex.toString());
    }
}
