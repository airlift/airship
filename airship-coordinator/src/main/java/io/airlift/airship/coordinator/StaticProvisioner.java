package io.airlift.airship.coordinator;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSortedMap;
import io.airlift.airship.shared.AgentStatusRepresentation;
import io.airlift.http.client.HttpClient;
import io.airlift.http.client.Request;
import io.airlift.http.server.HttpServerInfo;
import io.airlift.json.JsonCodec;

import javax.inject.Inject;
import javax.ws.rs.core.UriBuilder;
import java.net.URI;
import java.util.List;
import java.util.Map.Entry;
import java.util.SortedMap;

import static com.google.common.base.Objects.firstNonNull;
import static io.airlift.http.client.JsonResponseHandler.createJsonResponseHandler;

public class StaticProvisioner implements Provisioner
{
    private final String coordinatorInstanceId;
    private final URI coordinatorExternalUri;
    private final URI coordinatorInternalUri;

    private final SortedMap<String, String> agents;
    private final HttpClient httpClient;
    private final JsonCodec<AgentStatusRepresentation> agentCodec;

    @Inject
    public StaticProvisioner(LocalProvisionerConfig config,
            HttpServerInfo httpServerInfo,
            @Global HttpClient httpClient,
            JsonCodec<AgentStatusRepresentation> agentCodec)
    {
        this(httpServerInfo.getHttpExternalUri(),
                httpServerInfo.getHttpUri(),
                config.getLocalAgentUris(),
                httpClient,
                agentCodec);
    }

    public StaticProvisioner(URI coordinatorExternalUri,
            URI coordinatorInternalUri,
            List<String> agents,
            HttpClient httpClient,
            JsonCodec<AgentStatusRepresentation> agentCodec)
    {
        Preconditions.checkNotNull(coordinatorExternalUri, "coordinatorExternalUri is null");
        Preconditions.checkNotNull(coordinatorInternalUri, "coordinatorInternalUri is null");
        Preconditions.checkNotNull(agents, "agents is null");
        Preconditions.checkNotNull(httpClient, "httpClient is null");
        Preconditions.checkNotNull(agentCodec, "agentCodec is null");

        this.coordinatorExternalUri = coordinatorExternalUri;
        this.coordinatorInternalUri = coordinatorInternalUri;

        int instanceNumber = 1;
        coordinatorInstanceId = String.format("i-%05d", instanceNumber++);

        ImmutableSortedMap.Builder<String,String> builder = ImmutableSortedMap.naturalOrder();
        for (String agentUri : agents) {
            String instanceId = String.format("i-%05d", instanceNumber++);
            builder.put(instanceId, agentUri);
        }
        this.agents = builder.build();
        this.httpClient = httpClient;
        this.agentCodec = agentCodec;
    }

    @Override
    public List<Instance> listCoordinators()
    {
        return ImmutableList.of(new Instance(coordinatorInstanceId,
                "unknown",
                "/static/" + coordinatorExternalUri.getHost() + "/coordinator",
                coordinatorInternalUri,
                coordinatorExternalUri));
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
        for (Entry<String, String> entry : agents.entrySet()) {
            String instanceId = entry.getKey();
            String agentUri = entry.getValue();

            URI uri = UriBuilder.fromUri(agentUri).path("/v1/agent").build();
            Request request = Request.Builder.prepareGet()
                    .setUri(uri)
                    .build();

            try {
                AgentStatusRepresentation agent = httpClient.execute(request, createJsonResponseHandler(agentCodec));

                instances.add(new Instance(instanceId,
                        firstNonNull(agent.getInstanceType(), "unknown"),
                        firstNonNull(agent.getLocation(), "/static/" + uri.getHost() + "/agent"),
                        agent.getSelf(),
                        agent.getExternalUri()));
            }
            catch (Exception e) {
                instances.add(new Instance(instanceId,
                        "unknown",
                         "/static/" + uri.getHost() + "/agent",
                        uri,
                        uri));
            }
        }
        return ImmutableList.copyOf(instances.build());
    }

    @Override
    public List<Instance> provisionAgents(String agentConfig,
            int agentCount,
            String instanceType,
            String availabilityZone,
            String ami,
            String keyPair,
            String securityGroup)
    {
        throw new UnsupportedOperationException("Static provisioner does not support agent provisioning");
    }

    @Override
    public void terminateAgents(Iterable<String> instanceIds)
    {
        throw new UnsupportedOperationException("Static provisioner does not support agent termination");
    }
}
