package io.airlift.airship.coordinator;

import com.google.common.base.Preconditions;
import io.airlift.airship.shared.CoordinatorStatus;
import io.airlift.airship.shared.CoordinatorStatusRepresentation;
import io.airlift.http.client.HttpClient;
import io.airlift.http.client.Request;
import io.airlift.json.JsonCodec;

import java.net.URI;

import static io.airlift.airship.shared.CoordinatorLifecycleState.OFFLINE;
import static io.airlift.airship.shared.CoordinatorLifecycleState.PROVISIONING;
import static io.airlift.airship.shared.HttpUriBuilder.uriBuilderFrom;
import static io.airlift.http.client.JsonResponseHandler.createJsonResponseHandler;

public class HttpRemoteCoordinator
        implements RemoteCoordinator
{
    private final JsonCodec<CoordinatorStatusRepresentation> coordinatorStatusCodec;

    private CoordinatorStatus coordinatorStatus;
    private final HttpClient httpClient;

    public HttpRemoteCoordinator(CoordinatorStatus coordinatorStatus,
            String environment,
            HttpClient httpClient,
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
    public CoordinatorStatus status()
    {
        return coordinatorStatus;
    }

    @Override
    public void setInternalUri(URI internalUri)
    {
        coordinatorStatus = coordinatorStatus.changeInternalUri(internalUri);
    }

    @Override
    public void updateStatus()
    {
        URI internalUri = coordinatorStatus.getInternalUri();
        if (internalUri != null) {
            try {
                Request request = Request.Builder.prepareGet()
                        .setUri(uriBuilderFrom(internalUri).replacePath("/v1/coordinator/").build())
                        .build();
                CoordinatorStatusRepresentation coordinatorStatusRepresentation = httpClient.execute(request, createJsonResponseHandler(coordinatorStatusCodec));
                coordinatorStatus = coordinatorStatusRepresentation.toCoordinatorStatus(coordinatorStatus.getInstanceId(), coordinatorStatus.getInstanceType());
                return;
            }
            catch (Exception ignored) {
            }
        }

        // error talking to coordinator -- mark coordinator offline
        if (coordinatorStatus.getState() != PROVISIONING) {
            coordinatorStatus = coordinatorStatus.changeState(OFFLINE);
        }
    }

    public void setStatus(CoordinatorStatus coordinatorStatus)
    {
        Preconditions.checkNotNull(coordinatorStatus, "coordinatorStatus is null");
        this.coordinatorStatus = coordinatorStatus;
    }
}

