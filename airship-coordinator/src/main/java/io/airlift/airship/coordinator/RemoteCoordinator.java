package io.airlift.airship.coordinator;

import io.airlift.airship.shared.CoordinatorStatus;

import java.net.URI;

public interface RemoteCoordinator
{
    CoordinatorStatus status();

    void setInternalUri(URI internalUri);

    void updateStatus();
}
