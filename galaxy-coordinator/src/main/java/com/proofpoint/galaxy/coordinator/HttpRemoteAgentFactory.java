package com.proofpoint.galaxy.coordinator;

import com.google.inject.Inject;
import com.ning.http.client.AsyncHttpClient;
import com.proofpoint.galaxy.shared.InstallationRepresentation;
import com.proofpoint.galaxy.shared.SlotStatusRepresentation;
import com.proofpoint.json.JsonCodec;

import java.util.UUID;

public class HttpRemoteAgentFactory implements RemoteAgentFactory
{
    private final AsyncHttpClient httpClient;
    private final JsonCodec<InstallationRepresentation> installationCodec;
    private final JsonCodec<SlotStatusRepresentation> slotStatusCodec;

    @Inject
    public HttpRemoteAgentFactory(JsonCodec<InstallationRepresentation> installationCodec, JsonCodec<SlotStatusRepresentation> slotStatusCodec)
    {
        this.httpClient = new AsyncHttpClient();
        this.installationCodec = installationCodec;
        this.slotStatusCodec = slotStatusCodec;
    }

    @Override
    public RemoteAgent createRemoteAgent(UUID agentId)
    {
        return new HttpRemoteAgent(agentId, httpClient, installationCodec, slotStatusCodec);
    }
}
