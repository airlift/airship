package io.airlift.airship.coordinator;

import com.google.common.base.Charsets;
import com.google.common.base.Function;
import com.google.common.base.Preconditions;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.io.CharStreams;
import com.google.common.io.Files;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import io.airlift.airship.shared.AgentStatusRepresentation;
import io.airlift.airship.shared.CoordinatorStatusRepresentation;
import io.airlift.http.client.AsyncHttpClient;
import io.airlift.http.client.Request;
import io.airlift.http.client.Request.Builder;
import io.airlift.http.client.StringResponseHandler.StringResponse;
import io.airlift.json.JsonCodec;
import io.airlift.log.Logger;
import io.airlift.node.NodeInfo;

import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.ws.rs.core.UriBuilder;

import java.io.File;
import java.io.StringReader;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.google.common.base.Objects.firstNonNull;
import static com.google.common.base.Predicates.notNull;
import static com.google.common.base.Strings.isNullOrEmpty;
import static io.airlift.airship.coordinator.ValidatingResponseHandler.validate;
import static io.airlift.airship.shared.HttpUriBuilder.uriBuilderFrom;
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
    private final AsyncHttpClient httpClient;
    private final JsonCodec<CoordinatorStatusRepresentation> coordinatorCodec;
    private final JsonCodec<AgentStatusRepresentation> agentCodec;

    private final AtomicBoolean coordinatorsResourceIsUp = new AtomicBoolean(true);
    private final AtomicBoolean agentsResourceIsUp = new AtomicBoolean(true);
    private final Set<String> badAgentUris = Collections.newSetFromMap(new ConcurrentHashMap<String, Boolean>());

    @Inject
    public StaticProvisioner(StaticProvisionerConfig config,
            NodeInfo nodeInfo,
            @Global AsyncHttpClient httpClient,
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
            AsyncHttpClient httpClient,
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
                            coordinator.getLocation(),
                            coordinator.getSelf(),
                            coordinator.getExternalUri());
                }
                catch (Exception e) {
                    return new Instance(hostAndPort,
                            "unknown",
                            null,
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
            String securityGroup,
            String subnetId,
            String privateIpAddress,
            String provisioningScriptsArtifact)
    {
        throw new UnsupportedOperationException("Static provisioner does not support coordinator provisioning");
    }

    @Override
    public List<Instance> listAgents()
    {
        List<String> lines = readLines("agents", agentsUri, agentsResourceIsUp);

        List<URI> agentUris = FluentIterable.from(lines)
                .transform(validAgentUri())
                .filter(notNull())
                .toList();

        List<ListenableFuture<Instance>> futures = new ArrayList<>();
        for (URI agentUri : agentUris) {
            futures.add(getAgentInstance(agentUri));
        }
        return Futures.getUnchecked(Futures.allAsList(futures));
    }

    private ListenableFuture<Instance> getAgentInstance(URI agentUri)
    {
        URI uri = uriBuilderFrom(agentUri).replacePath("/v1/agent").build();
        Request request = prepareGet().setUri(uri).build();
        SettableFuture<Instance> future = SettableFuture.create();
        Futures.addCallback(
                httpClient.executeAsync(request, validate(createJsonResponseHandler(agentCodec))),
                agentStatusCallback(future, agentUri));
        return future;
    }

    private FutureCallback<AgentStatusRepresentation> agentStatusCallback(final SettableFuture<Instance> future, final URI uri)
    {
        return new FutureCallback<AgentStatusRepresentation>()
        {
            @Override
            public void onSuccess(AgentStatusRepresentation agent)
            {
                future.set(new Instance(agent.getInstanceId(),
                        firstNonNull(agent.getInstanceType(), "unknown"),
                        agent.getLocation(),
                        agent.getSelf(),
                        agent.getExternalUri()));
            }

            @Override
            public void onFailure(Throwable t)
            {
                log.debug(t, "Failed to get agent status");
                String hostAndPort = uri.getHost() + ":" + uri.getPort();
                future.set(new Instance(hostAndPort, "unknown", null, uri, uri));
            }
        };
    }

    @Override
    public List<Instance> provisionAgents(String agentConfig,
            int agentCount,
            String instanceType,
            String availabilityZone,
            String ami,
            String keyPair,
            String securityGroup,
            String subnetId,
            String privateIpAddress,
            String provisioningScriptsArtifact)
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
                log.info("Static provisioner connection for %s to %s succeeded", name, uri);
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

    private Function<String, URI> validAgentUri()
    {
        return new Function<String, URI>()
        {
            @Nullable
            @Override
            public URI apply(String agentUri)
            {
                try {
                    return validateAgentUri(agentUri);
                }
                catch (RuntimeException e) {
                    log.error(e, "Agent URI is invalid: %s", agentUri);
                    return null;
                }
            }
        };
    }

    private URI validateAgentUri(String agentUri)
    {
        agentUri = agentUri.trim();
        if (agentUri.isEmpty()) {
            return null;
        }

        URI uri;
        try {
            uri = new URI(agentUri);
        }
        catch (URISyntaxException e) {
            if (badAgentUris.add(agentUri)) {
                log.error("Agent URI is invalid: %s: %s", agentUri, e.getMessage());
            }
            return null;
        }

        if ((!uri.getScheme().equalsIgnoreCase("http")) && (!uri.getScheme().equalsIgnoreCase("https"))) {
            log.warn("Agent URI scheme must be http or https: %s", agentUri);
        }

        if ((!isNullOrEmpty(uri.getPath())) && (!uri.getPath().equals("/"))) {
            if (badAgentUris.add(agentUri)) {
                log.warn("Agent URI should not have a path: %s", agentUri);
            }
        }
        return uriBuilderFrom(uri).replacePath("/").build();
    }
}
