package com.proofpoint.galaxy.coordinator;

import com.google.inject.Inject;
import com.ning.http.client.AsyncHttpClient;
import com.proofpoint.galaxy.shared.AgentStatusRepresentation;
import com.proofpoint.galaxy.shared.InstallationRepresentation;
import com.proofpoint.galaxy.shared.SlotStatusRepresentation;
import com.proofpoint.json.JsonCodec;

import java.net.URI;

public class HttpRemoteAgentFactory implements RemoteAgentFactory
{
    private final AsyncHttpClient httpClient;
    private final JsonCodec<InstallationRepresentation> installationCodec;
    private final JsonCodec<AgentStatusRepresentation> agentStatusCodec;
    private final JsonCodec<SlotStatusRepresentation> slotStatusCodec;

    @Inject
    public HttpRemoteAgentFactory(JsonCodec<InstallationRepresentation> installationCodec,
            JsonCodec<SlotStatusRepresentation> slotStatusCodec,
            JsonCodec<AgentStatusRepresentation> agentStatusCodec)
    {
        this.agentStatusCodec = agentStatusCodec;
        this.httpClient = new AsyncHttpClient();
        this.installationCodec = installationCodec;
        this.slotStatusCodec = slotStatusCodec;
    }

    @Override
    public RemoteAgent createRemoteAgent(String agentId, URI uri)
    {
        return new HttpRemoteAgent(agentId, uri, httpClient, installationCodec, agentStatusCodec, slotStatusCodec);
    }
}
