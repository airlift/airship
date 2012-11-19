package io.airlift.airship.coordinator;

import com.google.common.base.Preconditions;
import io.airlift.airship.shared.CoordinatorLifecycleState;
import io.airlift.airship.shared.CoordinatorStatus;

import java.util.Map;

public class MockRemoteCoordinatorFactory
        implements RemoteCoordinatorFactory
{
    private final Map<String, CoordinatorStatus> coordinators;

    public MockRemoteCoordinatorFactory(Map<String, CoordinatorStatus> coordinators)
    {
        Preconditions.checkNotNull(coordinators, "coordinators is null");
        this.coordinators = coordinators;
    }

    @Override
    public RemoteCoordinator createRemoteCoordinator(Instance instance, CoordinatorLifecycleState state)
    {
        CoordinatorStatus coordinatorStatus = coordinators.get(instance.getInstanceId());
        Preconditions.checkArgument(coordinatorStatus != null, "Unknown instance %s", instance.getInstanceId());
        return new MockRemoteCoordinator(instance.getInstanceId(), coordinators);
    }
}
