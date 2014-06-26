package io.airlift.airship.shared;

import com.google.common.base.Function;
import com.google.common.base.Preconditions;

import javax.annotation.concurrent.Immutable;

import java.net.URI;

import static com.google.common.base.Objects.firstNonNull;

@Immutable
public class CoordinatorStatus
{
    private final String coordinatorId;
    private final CoordinatorLifecycleState state;
    private final String instanceId;
    private final URI internalUri;
    private final URI externalUri;
    private final String location;
    private final String instanceType;
    private final String version;

    public CoordinatorStatus(String coordinatorId, CoordinatorLifecycleState state, String instanceId, URI internalUri, URI externalUri, String location, String instanceType)
    {
        Preconditions.checkNotNull(state, "state is null");
        Preconditions.checkNotNull(instanceId, "instanceId is null");
        Preconditions.checkArgument(!instanceId.isEmpty(), "instanceId is empty");

        this.coordinatorId = coordinatorId;
        this.state = state;
        this.instanceId = instanceId;
        this.internalUri = internalUri;
        this.externalUri = externalUri;
        this.location = location;
        this.instanceType = instanceType;
        this.version = VersionsUtil.createVersion(coordinatorId, state);
    }

    public String getCoordinatorId()
    {
        return coordinatorId;
    }

    public CoordinatorLifecycleState getState()
    {
        return state;
    }

    public CoordinatorStatus changeState(CoordinatorLifecycleState state)
    {
        return new CoordinatorStatus(coordinatorId, state, instanceId, internalUri, externalUri, location, instanceType);
    }

    public CoordinatorStatus changeInternalUri(URI internalUri)
    {
        return new CoordinatorStatus(coordinatorId, state, instanceId, internalUri, externalUri, location, instanceType);
    }

    public String getInstanceId()
    {
        return instanceId;
    }

    public URI getInternalUri()
    {
        return internalUri;
    }

    public URI getExternalUri()
    {
        return externalUri;
    }

    public String getLocation()
    {
        return location;
    }

    public String getInstanceType()
    {
        return instanceType;
    }

    public String getVersion()
    {
        return version;
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

        CoordinatorStatus that = (CoordinatorStatus) o;

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
        sb.append("CoordinatorStatus");
        sb.append("{coordinatorId='").append(coordinatorId).append('\'');
        sb.append(", state=").append(state);
        sb.append(", instanceId='").append(instanceId).append('\'');
        sb.append(", internalUri=").append(internalUri);
        sb.append(", externalUri=").append(externalUri);
        sb.append(", location='").append(location).append('\'');
        sb.append(", instanceType='").append(instanceType).append('\'');
        sb.append(", version='").append(version).append('\'');
        sb.append('}');
        return sb.toString();
    }

    public static Function<CoordinatorStatus, String> idGetter()
    {
        return new Function<CoordinatorStatus, String>()
        {
            public String apply(CoordinatorStatus input)
            {
                return input.getCoordinatorId();
            }
        };
    }

    public static Function<CoordinatorStatus, String> locationGetter(final String defaultValue)
    {
        return new Function<CoordinatorStatus, String>()
        {
            public String apply(CoordinatorStatus input)
            {
                return firstNonNull(input.getLocation(), defaultValue);
            }
        };
    }
}
