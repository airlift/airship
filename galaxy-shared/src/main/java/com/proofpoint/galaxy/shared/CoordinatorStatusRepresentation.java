package com.proofpoint.galaxy.shared;

import com.google.common.base.Function;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import org.codehaus.jackson.annotate.JsonAutoDetect;
import org.codehaus.jackson.annotate.JsonCreator;
import org.codehaus.jackson.annotate.JsonMethod;
import org.codehaus.jackson.annotate.JsonProperty;

import javax.validation.constraints.NotNull;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.List;

@JsonAutoDetect(JsonMethod.NONE)
public class CoordinatorStatusRepresentation
{
    public static List<CoordinatorStatusRepresentation> toCoordinatorStatusRepresentations(Iterable<CoordinatorStatus> coordinators)
    {
        return ImmutableList.copyOf(Iterables.transform(coordinators, new Function<CoordinatorStatus, CoordinatorStatusRepresentation>()
        {
            @Override
            public CoordinatorStatusRepresentation apply(CoordinatorStatus coordinator)
            {
                return from(coordinator);
            }
        }));
    }

    public static final String GALAXY_COORDINATOR_VERSION_HEADER = "x-galaxy-coordinator-version";

    private final String coordinatorId;
    private final URI self;
    private final CoordinatorLifecycleState state;
    private final String location;
    private final String instanceType;
    private final String version;

    public static Function<CoordinatorStatus, CoordinatorStatusRepresentation> fromCoordinatorStatus()
    {
        return new Function<CoordinatorStatus, CoordinatorStatusRepresentation>()
        {
            public CoordinatorStatusRepresentation apply(CoordinatorStatus status)
            {
                return from(status);
            }
        };
    }

    public static CoordinatorStatusRepresentation from(CoordinatorStatus status)
    {
        return new CoordinatorStatusRepresentation(
                status.getCoordinatorId(),
                status.getState(),
                status.getUri(),
                status.getLocation(),
                status.getInstanceType(),
                status.getVersion());
    }

    @JsonCreator
    public CoordinatorStatusRepresentation(
            @JsonProperty("coordinatorId") String coordinatorId,
            @JsonProperty("state") CoordinatorLifecycleState state,
            @JsonProperty("self") URI self,
            @JsonProperty("location") String location,
            @JsonProperty("instanceType") String instanceType,
            @JsonProperty("version") String version)
    {
        this.coordinatorId = coordinatorId;
        this.self = self;
        this.state = state;
        this.location = location;
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
    public URI getSelf()
    {
        return self;
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
    public String getInstanceType()
    {
        return instanceType;
    }

    @JsonProperty
    public String getVersion()
    {
        return version;
    }

    public String getHost()
    {
        if (self == null) {
            return null;
        }
        return self.getHost();
    }

    public String getIp()
    {
        String host = getHost();
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

    public CoordinatorStatus toCoordinatorStatus()
    {
        return new CoordinatorStatus(coordinatorId, CoordinatorLifecycleState.ONLINE, self, location, instanceType);
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
        sb.append("{coordinatorId=").append(coordinatorId);
        sb.append(", state=").append(state);
        sb.append(", location=").append(location);
        sb.append(", instanceType=").append(instanceType);
        sb.append(", version=").append(version);
        sb.append('}');
        return sb.toString();
    }
}
