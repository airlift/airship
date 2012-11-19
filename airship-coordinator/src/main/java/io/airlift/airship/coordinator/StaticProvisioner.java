package io.airlift.airship.coordinator;

import com.google.common.base.Charsets;
import com.google.common.base.Function;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.io.CharStreams;
import com.google.common.io.Files;
import io.airlift.airship.shared.AgentStatusRepresentation;
import io.airlift.airship.shared.CoordinatorStatusRepresentation;
import io.airlift.http.client.HttpClient;
import io.airlift.http.client.Request;
import io.airlift.http.client.Request.Builder;
import io.airlift.http.client.StringResponseHandler.StringResponse;
import io.airlift.json.JsonCodec;
import io.airlift.log.Logger;
import io.airlift.node.NodeInfo;

import javax.inject.Inject;
import javax.ws.rs.core.UriBuilder;
import java.io.File;
import java.io.StringReader;
import java.net.URI;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.google.common.base.Objects.firstNonNull;
import static io.airlift.http.client.JsonResponseHandler.createJsonResponseHandler;
import static io.airlift.http.client.Request.Builder.prepareGet;
import static io.airlift.http.client.StringResponseHandler.createStringResponseHandler;

public class StaticProvisioner
        implements Provisioner
{
    private static final Logger log = Logger.get(StaticProvisioner.class);

    private final URI coordinatorsUri;
    private final URI agentsUri;
    private final NodeInfo nodeInfo;
    private final HttpClient httpClient;
    private final JsonCodec<CoordinatorStatusRepresentation> coordinatorCodec;
    private final JsonCodec<AgentStatusRepresentation> agentCodec;

    private final AtomicBoolean coordinatorsResourceIsUp = new AtomicBoolean(true);
    private final AtomicBoolean agentsResourceIsUp = new AtomicBoolean(true);

    @Inject
    public StaticProvisioner(StaticProvisionerConfig config,
            NodeInfo nodeInfo,
            @Global HttpClient httpClient,
            JsonCodec<CoordinatorStatusRepresentation> coordinatorCodec,
            JsonCodec<AgentStatusRepresentation> agentCodec)
    {
        this(config.getCoordinatorsUri(),
                config.getAgentsUri(),
                nodeInfo,
                httpClient,
                coordinatorCodec,
                agentCodec);
    }

    public StaticProvisioner(URI coordinatorsUri,
            URI agentsUri,
            NodeInfo nodeInfo,
            HttpClient httpClient,
            JsonCodec<CoordinatorStatusRepresentation> coordinatorCodec,
            JsonCodec<AgentStatusRepresentation> agentCodec)
    {
        Preconditions.checkNotNull(coordinatorsUri, "coordinatorsUri is null");
        Preconditions.checkNotNull(agentsUri, "agentsUri is null");
        Preconditions.checkNotNull(nodeInfo, "nodeInfo is null");
        Preconditions.checkNotNull(httpClient, "httpClient is null");
        Preconditions.checkNotNull(coordinatorCodec, "coordinatorCodec is null");
        Preconditions.checkNotNull(agentCodec, "agentCodec is null");

        this.nodeInfo = nodeInfo;
        this.httpClient = httpClient;

        this.coordinatorCodec = coordinatorCodec;
        this.agentCodec = agentCodec;

        this.agentsUri = agentsUri;
        String agentsUriScheme = agentsUri.getScheme().toLowerCase();
        Preconditions.checkArgument(agentsUriScheme.equals("http") || agentsUriScheme.equals("https") || agentsUriScheme.equals("file"), "Agents uri must have a http, https, or file scheme");

        this.coordinatorsUri = coordinatorsUri;
        String coordinatorsUriScheme = coordinatorsUri.getScheme().toLowerCase();
        Preconditions.checkArgument(coordinatorsUriScheme.equals("http") || coordinatorsUriScheme.equals("https") || coordinatorsUriScheme.equals("file"), "Coordinators uri must have a http, https, or file scheme");
    }

    @Override
    public List<Instance> listCoordinators()
    {
        List<String> lines = readLines("coordinators", coordinatorsUri, coordinatorsResourceIsUp);
        return ImmutableList.copyOf(Iterables.transform(lines, new Function<String, Instance>() {
            @Override
            public Instance apply(String coordinatorUri)
            {
                URI uri = UriBuilder.fromUri(coordinatorUri).path("/v1/coordinator").build();
                Request request = Builder.prepareGet()
                        .setUri(uri)
                        .build();

                String hostAndPort = uri.getHost() + ":" + uri.getPort();
                try {
                    CoordinatorStatusRepresentation coordinator = httpClient.execute(request, createJsonResponseHandler(coordinatorCodec));

                    return new Instance(coordinator.getInstanceId(),
                            firstNonNull(coordinator.getInstanceType(), "unknown"),
                            firstNonNull(coordinator.getLocation(), "/static/" + hostAndPort + "/coordinator"),
                            coordinator.getSelf(),
                            coordinator.getExternalUri());
                }
                catch (Exception e) {
                    return new Instance(hostAndPort,
                            "unknown",
                            "/static/" + hostAndPort + "/agent",
                            uri,
                            uri);
                }
            }
        }));
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
        List<String> lines = readLines("agents", agentsUri, agentsResourceIsUp);
        return ImmutableList.copyOf(Iterables.transform(lines, new Function<String, Instance>()
        {
            @Override
            public Instance apply(String agentUri)
            {

                URI uri = UriBuilder.fromUri(agentUri).path("/v1/agent").build();
                Request request = Builder.prepareGet()
                        .setUri(uri)
                        .build();

                String hostAndPort = uri.getHost() + ":" + uri.getPort();
                try {
                    AgentStatusRepresentation agent = httpClient.execute(request, createJsonResponseHandler(agentCodec));

                    return new Instance(agent.getInstanceId(),
                            firstNonNull(agent.getInstanceType(), "unknown"),
                            firstNonNull(agent.getLocation(), "/static/" + hostAndPort + "/agent"),
                            agent.getSelf(),
                            agent.getExternalUri());
                }
                catch (Exception e) {
                    return new Instance(hostAndPort,
                            "unknown",
                            "/static/" + hostAndPort + "/agent",
                            uri,
                            uri);
                }
            }
        }));
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

    private List<String> readLines(String name, URI uri, AtomicBoolean resourceUp)
    {
        try {
            String contents;
            if (uri.getScheme().toLowerCase().startsWith("http")) {
                Builder requestBuilder = prepareGet()
                        .setUri(uri)
                        .setHeader("User-Agent", nodeInfo.getNodeId());
                StringResponse stringResponse = httpClient.execute(requestBuilder.build(), createStringResponseHandler());
                if (stringResponse.getStatusCode() != 200) {
                    logServerError(resourceUp, "Error loading %s file from %s: statusCode=%s statusMessage=%s",
                            name,
                            uri,
                            stringResponse.getStatusCode(),
                            stringResponse.getStatusMessage());
                    return ImmutableList.of();
                }
                contents = stringResponse.getBody();
            }
            else {
                File file = new File(uri.getSchemeSpecificPart());
                contents = Files.toString(file, Charsets.UTF_8);
            }

            List<String> lines = CharStreams.readLines(new StringReader(contents));
            if (resourceUp.compareAndSet(false, true)) {
                log.info("ServiceInventory connect succeeded");
            }
            return lines;
        }
        catch (Exception e) {
            logServerError(resourceUp, "Error loading %s file from %s", name, uri);
            return ImmutableList.of();
        }
    }

    private void logServerError(AtomicBoolean resourceUp, String message, Object... args)
    {
        if (resourceUp.compareAndSet(true, false)) {
            log.error(message, args);
        }
    }

}
