package io.airlift.airship.coordinator;

import com.google.common.base.Preconditions;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import io.airlift.airship.shared.CoordinatorLifecycleState;
import io.airlift.airship.shared.CoordinatorStatus;

import java.net.URI;
import java.util.Map;

public class MockRemoteCoordinator
        implements RemoteCoordinator
{
    private final String instanceId;
    private final Map<String, CoordinatorStatus> coordinators;

    public MockRemoteCoordinator(String instanceId, Map<String, CoordinatorStatus> coordinators)
    {
        this.instanceId = instanceId;
        this.coordinators = coordinators;
    }

    @Override
    public CoordinatorStatus status()
    {
        return getCoordinatorStatus();
    }

    @Override
    public void setInternalUri(URI internalUri)
    {
        setCoordinatorStatus(getCoordinatorStatus().changeInternalUri(internalUri));
    }

    @Override
    public ListenableFuture<?> updateStatus()
    {
        return Futures.immediateFuture(null);
    }

    public CoordinatorStatus getCoordinatorStatus()
    {
        CoordinatorStatus coordinatorStatus = coordinators.get(instanceId);
        if (coordinatorStatus != null) {
            return coordinatorStatus;
        } else {
            return new CoordinatorStatus(null,
                    CoordinatorLifecycleState.OFFLINE,
                    instanceId,
                    null,
                    null,
                    null,
                    null);
        }
    }

    public void setCoordinatorStatus(CoordinatorStatus coordinatorStatus)
    {
        Preconditions.checkNotNull(coordinatorStatus, "coordinatorStatus is null");
        coordinators.put(instanceId, coordinatorStatus);
    }
}
