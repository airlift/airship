package io.airlift.airship.coordinator;

import com.google.common.base.Preconditions;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import io.airlift.airship.shared.CoordinatorStatus;
import io.airlift.airship.shared.CoordinatorStatusRepresentation;
import io.airlift.http.client.AsyncHttpClient;
import io.airlift.http.client.Request;
import io.airlift.json.JsonCodec;

import javax.annotation.concurrent.GuardedBy;

import java.net.URI;
import java.util.concurrent.atomic.AtomicLong;

import static io.airlift.airship.shared.CoordinatorLifecycleState.OFFLINE;
import static io.airlift.airship.shared.CoordinatorLifecycleState.PROVISIONING;
import static io.airlift.airship.shared.HttpUriBuilder.uriBuilderFrom;
import static io.airlift.http.client.JsonResponseHandler.createJsonResponseHandler;

public class HttpRemoteCoordinator
        implements RemoteCoordinator
{
    private final JsonCodec<CoordinatorStatusRepresentation> coordinatorStatusCodec;

    @GuardedBy("this")
    private CoordinatorStatus coordinatorStatus;
    private final AsyncHttpClient httpClient;

    private final AtomicLong failureCount = new AtomicLong();

    public HttpRemoteCoordinator(CoordinatorStatus coordinatorStatus,
            String environment,
            AsyncHttpClient httpClient,
            JsonCodec<CoordinatorStatusRepresentation> coordinatorStatusCodec)
    {
        Preconditions.checkNotNull(coordinatorStatus, "coordinatorStatus is null");
        Preconditions.checkNotNull(environment, "environment is null");
        Preconditions.checkNotNull(httpClient, "httpClient is null");

        this.coordinatorStatus = coordinatorStatus;
        this.httpClient = httpClient;
        this.coordinatorStatusCodec = coordinatorStatusCodec;
    }

    @Override
    public synchronized CoordinatorStatus status()
    {
        return coordinatorStatus;
    }

    @Override
    public synchronized void setInternalUri(URI internalUri)
    {
        coordinatorStatus = coordinatorStatus.changeInternalUri(internalUri);
    }

    @Override
    public ListenableFuture<?> updateStatus()
    {
        final CoordinatorStatus coordinatorStatus = status();
        URI internalUri = coordinatorStatus.getInternalUri();
        if (internalUri == null) {
            return Futures.immediateFuture(null);
        }

        Request request = Request.Builder.prepareGet()
                .setUri(uriBuilderFrom(internalUri).replacePath("/v1/coordinator/").build())
                .build();
        ListenableFuture<CoordinatorStatusRepresentation> future = httpClient.executeAsync(request, createJsonResponseHandler(coordinatorStatusCodec));
        Futures.addCallback(future, new FutureCallback<CoordinatorStatusRepresentation>()
        {
            @Override
            public void onSuccess(CoordinatorStatusRepresentation result)
            {
                // todo deal with out of order responses
                setStatus(result.toCoordinatorStatus(coordinatorStatus.getInstanceId(), coordinatorStatus.getInstanceType()));
                failureCount.set(0);
            }

            @Override
            public void onFailure(Throwable t)
            {
                // error talking to coordinator -- mark coordinator offline
                if (coordinatorStatus.getState() != PROVISIONING && failureCount.incrementAndGet() > 5) {
                    setStatus(coordinatorStatus.changeState(OFFLINE));
                }
            }
        });
        return future;
    }

    public synchronized void setStatus(CoordinatorStatus coordinatorStatus)
    {
        Preconditions.checkNotNull(coordinatorStatus, "coordinatorStatus is null");
        this.coordinatorStatus = coordinatorStatus;
    }
}

