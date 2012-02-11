package com.proofpoint.galaxy.shared;

import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;

import javax.annotation.concurrent.Immutable;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;

@Immutable
public class CoordinatorStatus
{
    private final String coordinatorId;
    private final CoordinatorLifecycleState state;
    private final URI internalUri;
    private final URI externalUri;
    private final String location;
    private final String instanceType;
    private final String version;

    public CoordinatorStatus(String coordinatorId, CoordinatorLifecycleState state, URI internalUri, URI externalUri, String location, String instanceType)
    {
        Preconditions.checkNotNull(coordinatorId, "coordinatorId is null");

        this.internalUri = internalUri;
        this.externalUri = externalUri;
        this.state = state;
        this.coordinatorId = coordinatorId;
        this.location = location;
        this.instanceType = instanceType;
        this.version = createVersion(coordinatorId, state);
    }

    public CoordinatorStatus(CoordinatorStatus coordinatorStatus, CoordinatorLifecycleState state)
    {
        Preconditions.checkNotNull(coordinatorStatus, "coordinatorStatus is null");
        Preconditions.checkNotNull(state, "state is null");

        this.internalUri = coordinatorStatus.internalUri;
        this.externalUri = coordinatorStatus.externalUri;
        this.state = state;
        this.coordinatorId = coordinatorStatus.coordinatorId;
        this.location = coordinatorStatus.location;
        this.instanceType = coordinatorStatus.instanceType;
        this.version = createVersion(coordinatorId, state);
    }

    public String getCoordinatorId()
    {
        return coordinatorId;
    }

    public CoordinatorLifecycleState getState()
    {
        return state;
    }
    
    public CoordinatorStatus updateState(CoordinatorLifecycleState state)
    {
        return new CoordinatorStatus(this, state);
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
        sb.append("{coordinatorId=").append(coordinatorId);
        sb.append(", state=").append(state);
        sb.append(", uri=").append(internalUri);
        sb.append(", version=").append(version);
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

    public static String createVersion(String coordinatorId, CoordinatorLifecycleState state)
    {
        List<Object> parts = new ArrayList<Object>();
        parts.add(coordinatorId);
        parts.add(state);

        String data = Joiner.on("||").useForNull("--NULL--").join(parts);
        return DigestUtils.md5Hex(data);
    }
}
