package io.airlift.airship.coordinator;

import com.google.common.util.concurrent.ListenableFuture;
import io.airlift.airship.shared.CoordinatorStatus;

import java.net.URI;

public interface RemoteCoordinator
{
    CoordinatorStatus status();

    void setInternalUri(URI internalUri);

    ListenableFuture<?> updateStatus();
}
