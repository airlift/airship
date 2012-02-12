package com.proofpoint.galaxy.coordinator;

import com.google.common.base.Function;
import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.collect.Lists;
import com.google.common.net.InetAddresses;
import com.proofpoint.galaxy.shared.CoordinatorLifecycleState;
import com.proofpoint.galaxy.shared.CoordinatorStatus;
import com.proofpoint.galaxy.shared.HttpUriBuilder;

import javax.annotation.Nullable;
import javax.ws.rs.core.UriInfo;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Map.Entry;
import java.util.regex.Pattern;

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
            else if ("ip" .equals(entry.getKey())) {
                for (String ipFilter : entry.getValue()) {
                    builder.addIpFilter(ipFilter);
                }
            }
        }
        return builder.buildPredicate();
    }

    private final List<CoordinatorLifecycleState> stateFilters = Lists.newArrayListWithCapacity(6);
    private final List<String> hostGlobs = Lists.newArrayListWithCapacity(6);
    private final List<String> ipFilters = Lists.newArrayListWithCapacity(6);

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

    public void addIpFilter(String ipFilter)
    {
        Preconditions.checkNotNull(ipFilter, "ipFilter is null");
        ipFilters.add(ipFilter);
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
        if (!ipFilters.isEmpty()) {
            Predicate<CoordinatorStatus> predicate = Predicates.or(Lists.transform(ipFilters, new Function<String, IpPredicate>()
            {
                @Override
                public IpPredicate apply(String ipFilter)
                {
                    return new IpPredicate(ipFilter);
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
        for (String ipFilter : ipFilters) {
            uriBuilder.addParameter("ip", ipFilter);
        }
        for (CoordinatorLifecycleState stateFilter : stateFilters) {
            uriBuilder.addParameter("state", stateFilter.name());
        }
        return uriBuilder.build();
    }

    public static class HostPredicate implements Predicate<CoordinatorStatus>
    {
        private final Predicate<CharSequence> predicate;

        public HostPredicate(String hostGlob)
        {
            predicate = new GlobPredicate(hostGlob.toLowerCase());
        }

        @Override
        public boolean apply(@Nullable CoordinatorStatus coordinatorStatus)
        {
            return coordinatorStatus != null &&
                    coordinatorStatus.getInternalUri() != null &&
                    coordinatorStatus.getInternalUri().getHost() != null &&
                    predicate.apply(coordinatorStatus.getInternalUri().getHost().toLowerCase());
        }
    }

    public static class IpPredicate implements Predicate<CoordinatorStatus>
    {
        private final Predicate<InetAddress> predicate;

        public IpPredicate(String ipFilter)
        {
            predicate = Predicates.equalTo(InetAddresses.forString(ipFilter));
        }

        @Override
        public boolean apply(@Nullable CoordinatorStatus coordinatorStatus)
        {
            try {
                return coordinatorStatus != null &&
                        coordinatorStatus.getInternalUri() != null &&
                        coordinatorStatus.getInternalUri().getHost() != null &&
                        predicate.apply(InetAddress.getByName(coordinatorStatus.getInternalUri().getHost()));
            }
            catch (UnknownHostException e) {
                return false;
            }
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
