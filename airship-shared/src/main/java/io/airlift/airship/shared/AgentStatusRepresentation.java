package io.airlift.airship.shared;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.Function;
import com.google.common.base.Objects;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableList.Builder;
import com.google.common.collect.ImmutableMap;

import javax.validation.constraints.NotNull;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Map;

import static com.google.common.collect.Lists.transform;
import static io.airlift.airship.shared.AgentStatus.idGetter;
import static io.airlift.airship.shared.AgentStatus.locationGetter;
import static io.airlift.airship.shared.Strings.commonPrefixSegments;
import static io.airlift.airship.shared.Strings.safeTruncate;
import static io.airlift.airship.shared.Strings.shortestUniquePrefix;
import static io.airlift.airship.shared.Strings.trimLeadingSegments;

public class AgentStatusRepresentation
{
    public static class AgentStatusRepresentationFactory {
        public static final int MIN_PREFIX_SIZE = 4;
        public static final int MIN_LOCATION_SEGMENTS = 2;

        private final int shortIdPrefixSize;
        private final int commonLocationParts;
        private final Repository repository;

        public AgentStatusRepresentationFactory()
        {
            this(Integer.MAX_VALUE, 0, null);
        }

        public AgentStatusRepresentationFactory(List<AgentStatus> agentStatuses, Repository repository)
        {
            this.shortIdPrefixSize = shortestUniquePrefix(transform(agentStatuses, idGetter()), MIN_PREFIX_SIZE);
            this.commonLocationParts = commonPrefixSegments('/', transform(agentStatuses, locationGetter("/")), MIN_LOCATION_SEGMENTS);
            this.repository = repository;
        }

        public AgentStatusRepresentationFactory(int shortIdPrefixSize, int commonLocationParts, Repository repository)
        {
            this.shortIdPrefixSize = shortIdPrefixSize;
            this.commonLocationParts = commonLocationParts;
            this.repository = repository;
        }

        public AgentStatusRepresentation create(AgentStatus status) {
            Builder<SlotStatusRepresentation> builder = ImmutableList.builder();
            for (SlotStatus slot : status.getSlotStatuses()) {
                builder.add(SlotStatusRepresentation.from(slot, Integer.MAX_VALUE, repository));
            }
            return new AgentStatusRepresentation(
                    status.getAgentId(),
                    safeTruncate(status.getAgentId(), shortIdPrefixSize),
                    status.getInstanceId(),
                    status.getState(),
                    status.getInternalUri(),
                    status.getExternalUri(),
                    status.getLocation(),
                    trimLeadingSegments(status.getLocation(), '/', commonLocationParts),
                    status.getInstanceType(),
                    builder.build(),
                    status.getResources(),
                    status.getVersion());
        }
    }

    private final String agentId;
    private final String shortAgentId;
    private final String instanceId;
    private final List<SlotStatusRepresentation> slots;
    private final URI self;
    private final URI externalUri;
    private final AgentLifecycleState state;
    private final String location;
    private final String shortLocation;
    private final String instanceType;
    private final Map<String, Integer> resources;
    private final String version;

    public static Function<AgentStatus, AgentStatusRepresentation> fromAgentStatus(List<AgentStatus> agentStatuses, Repository repository)
    {
        return fromAgentStatus(new AgentStatusRepresentationFactory(agentStatuses, repository));
    }

    public static Function<AgentStatus, AgentStatusRepresentation> fromAgentStatus(final AgentStatusRepresentationFactory factory)
    {
        return new Function<AgentStatus, AgentStatusRepresentation>()
        {
            public AgentStatusRepresentation apply(AgentStatus status)
            {
                return factory.create(status);
            }
        };
    }

    public static AgentStatusRepresentation from(AgentStatus status) {
        return new AgentStatusRepresentationFactory().create(status);
    }

    @JsonCreator
    public AgentStatusRepresentation(
            @JsonProperty("agentId") String agentId,
            @JsonProperty("shortAgentId") String shortAgentId,
            @JsonProperty("instanceId") String instanceId,
            @JsonProperty("state") AgentLifecycleState state,
            @JsonProperty("self") URI self,
            @JsonProperty("externalUri") URI externalUri,
            @JsonProperty("location") String location,
            @JsonProperty("shortLocation") String shortLocation,
            @JsonProperty("instanceType") String instanceType,
            @JsonProperty("slots") List<SlotStatusRepresentation> slots,
            @JsonProperty("resources") Map<String, Integer> resources,
            @JsonProperty("version") String version)
    {
        this.agentId = agentId;
        this.shortAgentId = shortAgentId;
        this.instanceId = instanceId;
        this.slots = slots;
        this.self = self;
        this.externalUri = externalUri;
        this.state = state;
        this.location = location;
        this.shortLocation = shortLocation;
        this.instanceType = instanceType;
        if (resources != null) {
            this.resources = ImmutableMap.copyOf(resources);
        }
        else {
            this.resources = ImmutableMap.of();
        }
        this.version = version;
    }

    @JsonProperty
    @NotNull
    public String getAgentId()
    {
        return agentId;
    }

    @JsonProperty
    @NotNull
    public String getShortAgentId()
    {
        return shortAgentId;
    }

    @JsonProperty
    @NotNull
    public String getInstanceId()
    {
        return instanceId;
    }

    @JsonProperty
    @NotNull
    public List<SlotStatusRepresentation> getSlots()
    {
        return slots;
    }

    @JsonProperty
    @NotNull
    public URI getSelf()
    {
        return self;
    }

    @JsonProperty
    @NotNull
    public URI getExternalUri()
    {
        return externalUri;
    }

    @JsonProperty
    public AgentLifecycleState getState()
    {
        return state;
    }

    @JsonProperty
    public String getLocation()
    {
        return location;
    }

    @JsonProperty
    public String getShortLocation()
    {
        return shortLocation;
    }

    @JsonProperty
    public String getInstanceType()
    {
        return instanceType;
    }

    @JsonProperty
    public Map<String, Integer> getResources()
    {
        return resources;
    }

    @JsonProperty
    public String getVersion()
    {
        return version;
    }

    public String getInternalHost() {
        if (self == null) {
            return null;
        }
        return self.getHost();
    }

    public String getInternalIp()
    {
        String host = getInternalHost();
        if (host == null) {
            return null;
        }

        if ("localhost".equalsIgnoreCase(host)) {
            return "127.0.0.1";
        }

        try {
            return InetAddress.getByName(host).getHostAddress();
        }
        catch (UnknownHostException e) {
            return "unknown";
        }
    }

    public String getExternalHost() {
        if (externalUri == null) {
            return null;
        }
        return externalUri.getHost();
    }

    public AgentStatus toAgentStatus(String instanceId, String instanceType)
    {
        Builder<SlotStatus> builder = ImmutableList.builder();
        for (SlotStatusRepresentation slot : slots) {
            builder.add(slot.toSlotStatus(instanceId));
        }
        return new AgentStatus(agentId, AgentLifecycleState.ONLINE, instanceId, self, externalUri, location, instanceType, builder.build(), resources);
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        AgentStatusRepresentation that = (AgentStatusRepresentation) o;
        return Objects.equal(agentId, that.agentId);
    }

    @Override
    public int hashCode()
    {
        return Objects.hashCode(agentId);
    }

    @Override
    public String toString()
    {
        final StringBuilder sb = new StringBuilder();
        sb.append("AgentStatusRepresentation");
        sb.append("{agentId='").append(agentId).append('\'');
        sb.append(", shortAgentId=").append(shortAgentId);
        sb.append(", instanceId=").append(instanceId);
        sb.append(", slots=").append(slots);
        sb.append(", self=").append(self);
        sb.append(", externalUri=").append(externalUri);
        sb.append(", state=").append(state);
        sb.append(", location='").append(location).append('\'');
        sb.append(", shortLocation='").append(shortLocation).append('\'');
        sb.append(", instanceType='").append(instanceType).append('\'');
        sb.append(", resources=").append(resources);
        sb.append(", version='").append(version).append('\'');
        sb.append('}');
        return sb.toString();
    }
}
