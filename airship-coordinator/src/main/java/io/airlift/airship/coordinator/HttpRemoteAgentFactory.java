package io.airlift.airship.coordinator;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import com.proofpoint.discovery.client.ServiceDescriptorsRepresentation;
import io.airlift.airship.shared.AgentLifecycleState;
import io.airlift.airship.shared.AgentStatus;
import io.airlift.airship.shared.AgentStatusRepresentation;
import io.airlift.airship.shared.InstallationRepresentation;
import io.airlift.airship.shared.SlotStatus;
import io.airlift.airship.shared.SlotStatusRepresentation;
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
