package io.airlift.airship.coordinator;

import io.airlift.airship.shared.CoordinatorLifecycleState;

public interface RemoteCoordinatorFactory
{
    RemoteCoordinator createRemoteCoordinator(Instance instance, CoordinatorLifecycleState state);
}
