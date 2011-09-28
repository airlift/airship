package com.proofpoint.galaxy.coordinator;

import com.google.common.base.Functions;
import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.collect.Collections2;
import com.google.common.collect.Lists;
import com.google.common.net.InetAddresses;
import com.proofpoint.galaxy.shared.BinarySpec;
import com.proofpoint.galaxy.shared.ConfigSpec;
import com.proofpoint.galaxy.shared.SlotLifecycleState;
import com.proofpoint.galaxy.shared.SlotStatus;

import javax.annotation.Nullable;
import javax.ws.rs.core.UriInfo;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Collection;
import java.util.List;
import java.util.Map.Entry;
import java.util.UUID;
import java.util.regex.Pattern;

import static com.google.common.base.Functions.compose;
import static com.proofpoint.galaxy.coordinator.StringFunctions.startsWith;
import static com.proofpoint.galaxy.coordinator.StringFunctions.toLowerCase;
import static com.proofpoint.galaxy.shared.SlotStatus.uuidGetter;
import static java.lang.String.format;

public class SlotFilterBuilder
{
    public static Predicate<SlotStatus> build(UriInfo uriInfo, boolean filterRequired, List<UUID> allUuids)
    {
        SlotFilterBuilder builder = new SlotFilterBuilder(filterRequired);
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
                for (String shortId : entry.getValue()) {
                    Predicate<UUID> startsWithPrefix = Predicates.compose(startsWith(shortId.toLowerCase()), compose(toLowerCase(), StringFunctions.<UUID>toStringFunction()));
                    Collection<UUID> matches = Collections2.filter(allUuids, startsWithPrefix);

                    if (matches.size() > 1) {
                        throw new IllegalArgumentException(format("Ambiguous expansion for id '%s': %s", shortId, matches));
                    }

                    if (matches.isEmpty()) {
                        builder.shortCircuit();
                        break;
                    }
                    else {
                        builder.addSlotUuidFilter(matches.iterator().next());
                    }
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

    private final boolean filterRequired;
    private boolean shortCircuit;
    private final List<StatePredicate> stateFilters = Lists.newArrayListWithCapacity(6);
    private final List<SlotUuidPredicate> slotUuidFilters = Lists.newArrayListWithCapacity(6);
    private final List<HostPredicate> hostFilters = Lists.newArrayListWithCapacity(6);
    private final List<IpPredicate> ipFilters = Lists.newArrayListWithCapacity(6);
    private final List<BinarySpecPredicate> binarySpecPredicates = Lists.newArrayListWithCapacity(6);
    private final List<ConfigSpecPredicate> configSpecPredicates = Lists.newArrayListWithCapacity(6);

    private SlotFilterBuilder(boolean filterRequired)
    {
        this.filterRequired = filterRequired;
    }

    private void shortCircuit()
    {
        shortCircuit = true;
    }

    public void addStateFilter(String stateFilter)
    {
        Preconditions.checkNotNull(stateFilter, "stateFilter is null");
        SlotLifecycleState state = SlotLifecycleState.lookup(stateFilter);
        Preconditions.checkArgument(state != null, "unknown state " + stateFilter);
        stateFilters.add(new StatePredicate(state));
    }

    public void addSlotUuidFilter(UUID uuid)
    {
        Preconditions.checkNotNull(uuid, "uuid is null");
        slotUuidFilters.add(new SlotUuidPredicate(uuid));
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

    public Predicate<SlotStatus> build()
    {
        if (shortCircuit) {
            return Predicates.alwaysFalse();
        }

        // Filters are evaluated as: set | host | (env & version & type)
        List<Predicate<SlotStatus>> andPredicates = Lists.newArrayListWithCapacity(6);
        if (!stateFilters.isEmpty()) {
            Predicate<SlotStatus> predicate = Predicates.or(stateFilters);
            andPredicates.add(predicate);
        }
        if (!slotUuidFilters.isEmpty()) {
            Predicate<SlotStatus> predicate = Predicates.or(slotUuidFilters);
            andPredicates.add(predicate);
        }
        if (!hostFilters.isEmpty()) {
            Predicate<SlotStatus> predicate = Predicates.or(hostFilters);
            andPredicates.add(predicate);
        }
        if (!ipFilters.isEmpty()) {
            Predicate<SlotStatus> predicate = Predicates.or(ipFilters);
            andPredicates.add(predicate);
        }
        if (!binarySpecPredicates.isEmpty()) {
            Predicate<SlotStatus> predicate = Predicates.or(binarySpecPredicates);
            andPredicates.add(predicate);
        }
        if (!configSpecPredicates.isEmpty()) {
            Predicate<SlotStatus> predicate = Predicates.or(configSpecPredicates);
            andPredicates.add(predicate);
        }
        if (!andPredicates.isEmpty()) {
            return Predicates.and(andPredicates);
        }
        else if (!filterRequired) {
            return Predicates.alwaysTrue();
        }
        else {
            throw new InvalidSlotFilterException();
        }
    }

    public static class SlotUuidPredicate implements Predicate<SlotStatus>
    {
        private final UUID uuid;

        public SlotUuidPredicate(UUID uuid)
        {
            this.uuid = uuid;
        }

        @Override
        public boolean apply(@Nullable SlotStatus slotStatus)
        {
            return slotStatus != null &&
                    slotStatus.getId() != null &&
                    uuid.equals(slotStatus.getId());
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
        private final SlotLifecycleState state;

        public StatePredicate(SlotLifecycleState state)
        {
            this.state = state;
        }

        @Override
        public boolean apply(@Nullable SlotStatus slotStatus)
        {
            return slotStatus.getState() == state;
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
            BinarySpec binary = slotStatus.getAssignment().getBinary();
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
            ConfigSpec config = slotStatus.getAssignment().getConfig();
            return config != null &&
                    componentGlob.apply(config.getComponent()) &&
                    environmentGlob.apply(config.getEnvironment()) &&
                    poolGlob.apply(config.getPool()) &&
                    versionGlob.apply(config.getVersion());

        }
    }

    public static class GlobPredicate extends RegexPredicate
    {
        private final String glob;

        public GlobPredicate(String glob)
        {
            super(globToPattern(glob));
            this.glob = glob;
        }

        @Override
        public String toString()
        {
            return glob;
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

        @Override
        public String toString()
        {
            return pattern.pattern();
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
