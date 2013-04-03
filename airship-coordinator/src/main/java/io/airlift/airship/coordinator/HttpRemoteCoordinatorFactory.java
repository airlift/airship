package io.airlift.airship.coordinator;

import com.google.inject.Inject;
import io.airlift.airship.shared.CoordinatorLifecycleState;
import io.airlift.airship.shared.CoordinatorStatus;
import io.airlift.airship.shared.CoordinatorStatusRepresentation;
import io.airlift.http.client.AsyncHttpClient;
import io.airlift.json.JsonCodec;
import io.airlift.node.NodeInfo;

public class HttpRemoteCoordinatorFactory
        implements RemoteCoordinatorFactory
{
    private final String environment;
    private final AsyncHttpClient httpClient;
    private final JsonCodec<CoordinatorStatusRepresentation> coordinatorStatusCodec;

    @Inject
    public HttpRemoteCoordinatorFactory(NodeInfo nodeInfo,
            @Global AsyncHttpClient httpClient,
            JsonCodec<CoordinatorStatusRepresentation> coordinatorStatusCodec)
    {
        environment = nodeInfo.getEnvironment();
        this.coordinatorStatusCodec = coordinatorStatusCodec;
        this.httpClient = httpClient;
    }

    @Override
    public RemoteCoordinator createRemoteCoordinator(Instance instance, CoordinatorLifecycleState state)
    {
        CoordinatorStatus coordinatorStatus = new CoordinatorStatus(null,
                state,
                instance.getInstanceId(),
                instance.getInternalUri(),
                instance.getExternalUri(),
                instance.getLocation(),
                instance.getInstanceType());

        return new HttpRemoteCoordinator(coordinatorStatus, environment, httpClient, coordinatorStatusCodec);
    }
}
