package io.airlift.airship.coordinator;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import io.airlift.airship.shared.AgentLifecycleState;
import io.airlift.airship.shared.AgentStatus;
import io.airlift.airship.shared.AgentStatusRepresentation;
import io.airlift.airship.shared.InstallationRepresentation;
import io.airlift.airship.shared.SlotStatus;
import io.airlift.airship.shared.SlotStatusRepresentation;
import io.airlift.discovery.client.ServiceDescriptorsRepresentation;
import io.airlift.http.client.AsyncHttpClient;
import io.airlift.json.JsonCodec;
import io.airlift.node.NodeInfo;

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
            @Global AsyncHttpClient httpClient,
            JsonCodec<InstallationRepresentation> installationCodec,
            JsonCodec<SlotStatusRepresentation> slotStatusCodec,
            JsonCodec<AgentStatusRepresentation> agentStatusCodec,
            JsonCodec<ServiceDescriptorsRepresentation> serviceDescriptorsCodec)
    {
        environment = nodeInfo.getEnvironment();
        this.httpClient = httpClient;
        this.agentStatusCodec = agentStatusCodec;
        this.installationCodec = installationCodec;
        this.slotStatusCodec = slotStatusCodec;
        this.serviceDescriptorsCodec = serviceDescriptorsCodec;
    }

    @Override
    public RemoteAgent createRemoteAgent(Instance instance, AgentLifecycleState state)
    {
        AgentStatus agentStatus = new AgentStatus(null,
                state,
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
