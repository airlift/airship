package com.proofpoint.galaxy.coordinator;

import com.google.inject.Inject;
import com.ning.http.client.AsyncHttpClient;
import com.proofpoint.discovery.client.ServiceDescriptorsRepresentation;
import com.proofpoint.galaxy.shared.AgentStatus;
import com.proofpoint.galaxy.shared.AgentStatusRepresentation;
import com.proofpoint.galaxy.shared.InstallationRepresentation;
import com.proofpoint.galaxy.shared.SlotStatusRepresentation;
import com.proofpoint.json.JsonCodec;
import com.proofpoint.node.NodeInfo;

import java.net.URI;

public class HttpRemoteAgentFactory implements RemoteAgentFactory
{
    private final String environment;
    private final AsyncHttpClient httpClient;
    private final JsonCodec<InstallationRepresentation> installationCodec;
    private final JsonCodec<AgentStatusRepresentation> agentStatusCodec;
    private final JsonCodec<SlotStatusRepresentation> slotStatusCodec;
    private final JsonCodec<ServiceDescriptorsRepresentation> serviceDescriptorsCodec;

    @Inject
    public HttpRemoteAgentFactory(NodeInfo nodeInfo,
            JsonCodec<InstallationRepresentation> installationCodec,
            JsonCodec<SlotStatusRepresentation> slotStatusCodec,
            JsonCodec<AgentStatusRepresentation> agentStatusCodec,
            JsonCodec<ServiceDescriptorsRepresentation> serviceDescriptorsCodec)
    {
        environment = nodeInfo.getEnvironment();
        this.agentStatusCodec = agentStatusCodec;
        this.httpClient = new AsyncHttpClient();
        this.installationCodec = installationCodec;
        this.slotStatusCodec = slotStatusCodec;
        this.serviceDescriptorsCodec = serviceDescriptorsCodec;
    }

    @Override
    public RemoteAgent createRemoteAgent(AgentStatus agentStatus)
    {
        return new HttpRemoteAgent(agentStatus, environment, httpClient, installationCodec, agentStatusCodec, slotStatusCodec, serviceDescriptorsCodec);
    }
}
