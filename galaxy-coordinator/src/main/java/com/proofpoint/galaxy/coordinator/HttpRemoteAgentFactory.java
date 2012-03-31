package com.proofpoint.galaxy.coordinator;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import com.proofpoint.discovery.client.ServiceDescriptorsRepresentation;
import com.proofpoint.galaxy.shared.AgentLifecycleState;
import com.proofpoint.galaxy.shared.AgentStatus;
import com.proofpoint.galaxy.shared.AgentStatusRepresentation;
import com.proofpoint.galaxy.shared.InstallationRepresentation;
import com.proofpoint.galaxy.shared.SlotStatus;
import com.proofpoint.galaxy.shared.SlotStatusRepresentation;
import com.proofpoint.http.client.ApacheHttpClient;
import com.proofpoint.http.client.HttpClient;
import com.proofpoint.json.JsonCodec;
import com.proofpoint.node.NodeInfo;

public class HttpRemoteAgentFactory implements RemoteAgentFactory
{
    private final String environment;
    private final HttpClient httpClient;
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
        this.httpClient = new ApacheHttpClient();
        this.installationCodec = installationCodec;
        this.slotStatusCodec = slotStatusCodec;
        this.serviceDescriptorsCodec = serviceDescriptorsCodec;
    }

    @Override
    public RemoteAgent createRemoteAgent(Instance instance)
    {
        AgentStatus agentStatus = new AgentStatus("unknown",
                AgentLifecycleState.ONLINE,
                instance.getInstanceId(),
                instance.getInternalUri(),
                instance.getExternalUri(),
                instance.getLocation(),
                instance.getInstanceType(),
                ImmutableList.<SlotStatus>of(),
                ImmutableMap.<String, Integer>of());

        return new HttpRemoteAgent(agentStatus, environment, httpClient, installationCodec, agentStatusCodec, slotStatusCodec, serviceDescriptorsCodec);
    }
}
