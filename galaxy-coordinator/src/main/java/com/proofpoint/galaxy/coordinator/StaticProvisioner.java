package com.proofpoint.galaxy.coordinator;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.proofpoint.galaxy.shared.AgentStatusRepresentation;
import com.proofpoint.galaxy.shared.JsonResponseHandler;
import com.proofpoint.http.client.HttpClient;
import com.proofpoint.http.client.Request;
import com.proofpoint.http.client.RequestBuilder;
import com.proofpoint.json.JsonCodec;

import javax.inject.Inject;
import javax.ws.rs.core.UriBuilder;
import java.net.URI;
import java.util.List;

import static com.google.common.base.Objects.firstNonNull;

public class StaticProvisioner implements Provisioner
{
    private final List<String> localAgentUris;
    private final HttpClient httpClient;
    private final JsonCodec<AgentStatusRepresentation> agentCodec;

    @Inject
    public StaticProvisioner(LocalProvisionerConfig config, @Global HttpClient httpClient, JsonCodec<AgentStatusRepresentation> agentCodec)
    {
        this(config.getLocalAgentUris(), httpClient, agentCodec);
    }

    public StaticProvisioner(List<String> agentUris, HttpClient httpClient, JsonCodec<AgentStatusRepresentation> agentCodec)
    {
        Preconditions.checkNotNull(agentUris, "agentUris is null");
        Preconditions.checkNotNull(httpClient, "httpClient is null");
        Preconditions.checkNotNull(agentCodec, "agentCodec is null");

        this.localAgentUris = ImmutableList.copyOf(agentUris);
        this.httpClient = httpClient;
        this.agentCodec = agentCodec;
    }

    @Override
    public List<Instance> listCoordinators()
    {
        return ImmutableList.of();
    }

    @Override
    public List<Instance> provisionCoordinators(String coordinatorConfigSpec,
            int coordinatorCount,
            String instanceType,
            String availabilityZone,
            String ami,
            String keyPair,
            String securityGroup)
    {
        throw new UnsupportedOperationException("Static provisioner does not support coordinator provisioning");
    }

    @Override
    public List<Instance> listAgents()
    {
        ImmutableList.Builder<Instance> instances = ImmutableList.builder();
        for (String localAgentUri : localAgentUris) {
            URI uri = UriBuilder.fromUri(localAgentUri).path("/v1/agent").build();
            Request request = RequestBuilder.prepareGet()
                    .setUri(uri)
                    .build();

            try {
                AgentStatusRepresentation agent = httpClient.execute(request, JsonResponseHandler.create(agentCodec)).checkedGet();

                instances.add(new Instance(agent.getAgentId(),
                        firstNonNull(agent.getInstanceType(), "unknown"),
                        firstNonNull(agent.getLocation(), "unknown"),
                        agent.getSelf(),
                        agent.getExternalUri()));
            }
            catch (Exception e) {
                instances.add(new Instance(localAgentUri,
                        "unknown",
                        "unknown",
                        uri,
                        uri));
            }
        }
        return ImmutableList.copyOf(instances.build());
    }

    @Override
    public List<Instance> provisionAgents(String agentConfig, int agentCount, String instanceType, String availabilityZone)
            throws Exception
    {
        throw new UnsupportedOperationException("Static provisioner does not support agent provisioning");
    }

    @Override
    public void terminateAgents(List<String> instanceIds)
    {
        throw new UnsupportedOperationException("Static provisioner does not support agent termination");
    }
}
