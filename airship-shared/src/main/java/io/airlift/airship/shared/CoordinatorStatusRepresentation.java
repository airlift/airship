package io.airlift.airship.shared;

import com.google.common.base.Function;
import org.codehaus.jackson.annotate.JsonAutoDetect;
import org.codehaus.jackson.annotate.JsonCreator;
import org.codehaus.jackson.annotate.JsonMethod;
import org.codehaus.jackson.annotate.JsonProperty;

import javax.validation.constraints.NotNull;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.List;

import static com.google.common.collect.Lists.transform;
import static io.airlift.airship.shared.CoordinatorStatus.idGetter;
import static io.airlift.airship.shared.CoordinatorStatus.locationGetter;
import static io.airlift.airship.shared.Strings.commonPrefixSegments;
import static io.airlift.airship.shared.Strings.safeTruncate;
import static io.airlift.airship.shared.Strings.shortestUniquePrefix;
import static io.airlift.airship.shared.Strings.trimLeadingSegments;

@JsonAutoDetect(JsonMethod.NONE)
public class CoordinatorStatusRepresentation
{
    public static class CoordinatorStatusRepresentationFactory {
        public static final int MIN_PREFIX_SIZE = 4;
        public static final int MIN_LOCATION_SEGMENTS = 2;

        private final int shortIdPrefixSize;
        private final int commonLocationParts;

        public CoordinatorStatusRepresentationFactory()
        {
            this(Integer.MAX_VALUE, 0);
        }

        public CoordinatorStatusRepresentationFactory(List<CoordinatorStatus> coordinatorStatuses)
        {
            this.shortIdPrefixSize = shortestUniquePrefix(transform(coordinatorStatuses, idGetter()), MIN_PREFIX_SIZE);
            this.commonLocationParts = commonPrefixSegments('/', transform(coordinatorStatuses, locationGetter("/")), MIN_LOCATION_SEGMENTS);
        }

        public CoordinatorStatusRepresentationFactory(int shortIdPrefixSize, int commonLocationParts)
        {
            this.shortIdPrefixSize = shortIdPrefixSize;
            this.commonLocationParts = commonLocationParts;
        }

        public CoordinatorStatusRepresentation create(CoordinatorStatus status) {
            return new CoordinatorStatusRepresentation(
                    status.getCoordinatorId(),
                    safeTruncate(status.getCoordinatorId(), shortIdPrefixSize),
                    status.getInstanceId(),
                    status.getState(),
                    status.getInternalUri(),
                    status.getExternalUri(),
                    status.getLocation(),
                    trimLeadingSegments(status.getLocation(), '/', commonLocationParts),
                    status.getInstanceType(),
                    status.getVersion());
        }
    }

    private final String coordinatorId;
    private final String shortCoordinatorId;
    private final String instanceId;
    private final URI self;
    private final URI externalUri;
    private final CoordinatorLifecycleState state;
    private final String location;
    private final String shortLocation;
    private final String instanceType;
    private final String version;

    public static Function<CoordinatorStatus, CoordinatorStatusRepresentation> fromCoordinatorStatus(List<CoordinatorStatus> coordinatorStatuses)
    {
        return fromCoordinatorStatus(new CoordinatorStatusRepresentationFactory(coordinatorStatuses));
    }

    public static Function<CoordinatorStatus, CoordinatorStatusRepresentation> fromCoordinatorStatus(final CoordinatorStatusRepresentationFactory factory)
    {
        return new Function<CoordinatorStatus, CoordinatorStatusRepresentation>()
        {
            public CoordinatorStatusRepresentation apply(CoordinatorStatus status)
            {
                return factory.create(status);
            }
        };
    }

    public static CoordinatorStatusRepresentation from(CoordinatorStatus status)
    {
        return new CoordinatorStatusRepresentationFactory().create(status);
    }

    @JsonCreator
    public CoordinatorStatusRepresentation(
            @JsonProperty("coordinatorId") String coordinatorId,
            @JsonProperty("shortCoordinatorId") String shortCoordinatorId,
            @JsonProperty("instanceId") String instanceId,
            @JsonProperty("state") CoordinatorLifecycleState state,
            @JsonProperty("self") URI self,
            @JsonProperty("externalUri") URI externalUri,
            @JsonProperty("location") String location,
            @JsonProperty("shortLocation") String shortLocation,
            @JsonProperty("instanceType") String instanceType,
            @JsonProperty("version") String version)
    {
        this.coordinatorId = coordinatorId;
        this.shortCoordinatorId = shortCoordinatorId;
        this.instanceId = instanceId;
        this.self = self;
        this.state = state;
        this.externalUri = externalUri;
        this.location = location;
        this.shortLocation = shortLocation;
        this.instanceType = instanceType;
        this.version = version;
    }

    @JsonProperty
    @NotNull
    public String getCoordinatorId()
    {
        return coordinatorId;
    }

    @JsonProperty
    @NotNull
    public String getShortCoordinatorId()
    {
        return shortCoordinatorId;
    }

    @JsonProperty
    @NotNull
    public String getInstanceId()
    {
        return instanceId;
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
    public CoordinatorLifecycleState getState()
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
    public String getVersion()
    {
        return version;
    }

    public String getExternalHost()
    {
        if (externalUri == null) {
            return null;
        }
        return externalUri.getHost();
    }

    public String getInternalHost()
    {
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

    public CoordinatorStatus toCoordinatorStatus(String instanceId, String instanceType)
    {
        return new CoordinatorStatus(coordinatorId, CoordinatorLifecycleState.ONLINE, instanceId, self, externalUri, location, instanceType);
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

        CoordinatorStatusRepresentation that = (CoordinatorStatusRepresentation) o;

        if (!coordinatorId.equals(that.coordinatorId)) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode()
    {
        return coordinatorId.hashCode();
    }

    @Override
    public String toString()
    {
        final StringBuilder sb = new StringBuilder();
        sb.append("CoordinatorStatusRepresentation");
        sb.append("{coordinatorId='").append(coordinatorId).append('\'');
        sb.append(", shortCoordinatorId='").append(shortCoordinatorId).append('\'');
        sb.append(", instanceId='").append(instanceId).append('\'');
        sb.append(", self=").append(self);
        sb.append(", externalUri=").append(externalUri);
        sb.append(", state=").append(state);
        sb.append(", location='").append(location).append('\'');
        sb.append(", shortLocation='").append(shortLocation).append('\'');
        sb.append(", instanceType='").append(instanceType).append('\'');
        sb.append(", version='").append(version).append('\'');
        sb.append('}');
        return sb.toString();
    }
}
