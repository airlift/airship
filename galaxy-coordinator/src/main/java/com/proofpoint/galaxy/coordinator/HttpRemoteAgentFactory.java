package com.proofpoint.galaxy.coordinator;

import com.google.common.base.Ticker;
import com.google.inject.Inject;
import com.ning.http.client.AsyncHttpClient;
import com.proofpoint.galaxy.shared.InstallationRepresentation;
import com.proofpoint.galaxy.shared.SlotStatusRepresentation;
import com.proofpoint.json.JsonCodec;

public class HttpRemoteAgentFactory implements RemoteAgentFactory
{
    private final AsyncHttpClient httpClient;
    private final JsonCodec<InstallationRepresentation> installationCodec;
    private final JsonCodec<SlotStatusRepresentation> slotStatusCodec;
    private final Ticker ticker;

    @Inject
    public HttpRemoteAgentFactory(JsonCodec<InstallationRepresentation> installationCodec, JsonCodec<SlotStatusRepresentation> slotStatusCodec)
    {
        this(installationCodec, slotStatusCodec, Ticker.systemTicker());
    }

    public HttpRemoteAgentFactory(JsonCodec<InstallationRepresentation> installationCodec, JsonCodec<SlotStatusRepresentation> slotStatusCodec, Ticker ticker)
    {
        this.ticker = ticker;
        this.httpClient = new AsyncHttpClient();
        this.installationCodec = installationCodec;
        this.slotStatusCodec = slotStatusCodec;
    }

    @Override
    public RemoteAgent createRemoteAgent(String agentId)
    {
        return new HttpRemoteAgent(agentId, httpClient, installationCodec, slotStatusCodec, ticker);
    }
}
