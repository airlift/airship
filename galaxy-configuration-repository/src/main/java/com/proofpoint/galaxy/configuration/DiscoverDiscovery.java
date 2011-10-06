package com.proofpoint.galaxy.configuration;

import com.google.common.collect.ImmutableList;
import com.google.common.io.Closeables;
import com.google.common.io.InputSupplier;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.inject.Inject;
import com.ning.http.client.AsyncHttpClient;
import com.ning.http.client.Response;
import com.proofpoint.galaxy.shared.BinarySpec;
import com.proofpoint.galaxy.shared.ConfigSpec;
import com.proofpoint.galaxy.shared.SlotStatusRepresentation;
import com.proofpoint.http.server.HttpServerConfig;
import com.proofpoint.json.JsonCodec;
import com.proofpoint.log.Logger;
import com.proofpoint.node.NodeInfo;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.io.InputStream;
import java.net.ConnectException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public class DiscoverDiscovery
{
    private static final Logger log = Logger.get(DiscoverDiscovery.class);
    private static final int DEFAULT_DISCOVERY_PORT = new HttpServerConfig().getHttpPort();

    private final String environment;
    private final GitConfigurationRepository configurationRepository;
    private final JsonCodec<List<SlotStatusRepresentation>> codec;
    private final AsyncHttpClient client;
    private final String coordinatorSlotsUrl;
    private final AtomicReference<List<URI>> discoveryServers = new AtomicReference<List<URI>>(ImmutableList.<URI>of());
    private ScheduledExecutorService executor;
    private ScheduledFuture<?> discoverTask;

    @Inject
    public DiscoverDiscovery(NodeInfo nodeInfo,
            ConfigurationRepositoryConfig config,
            GitConfigurationRepository configurationRepository,
            JsonCodec<List<SlotStatusRepresentation>> codec)
    {
        environment = nodeInfo.getEnvironment();
        this.configurationRepository = configurationRepository;
        this.codec = codec;
        this.coordinatorSlotsUrl = config.getCoordinatorBaseURI().resolve("/v1/slot/").toString();
        this.client = new AsyncHttpClient();
        this.executor = Executors.newSingleThreadScheduledExecutor(new ThreadFactoryBuilder().setDaemon(true).setNameFormat("discover-discovery-%s").build());
    }

    @PostConstruct
    public synchronized void start()
    {
        // already started?
        if (discoverTask != null) {
            return;
        }

        discoverTask = executor.scheduleWithFixedDelay(new Runnable()
        {
            @Override
            public void run()
            {
                try {
                    discoverDiscovery();
                }
                catch (RuntimeException e) {
                    log.error(e, "Unexpected exception in discovery discovery");
                }
                catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }, 0, 3, TimeUnit.SECONDS);

    }

    @PreDestroy
    public synchronized void stop()
    {
        // already stopped?
        if (discoverTask == null) {
            return;
        }

        discoverTask.cancel(true);
        discoverTask = null;
    }

    public List<URI> getDiscoveryServers()
    {
        return discoveryServers.get();
    }

    public synchronized void discoverDiscovery()
            throws InterruptedException
    {
        List<SlotStatusRepresentation> slots;
        try {
            Response response = client.prepareGet(coordinatorSlotsUrl)
                    .execute()
                    .get();
            if (response.getStatusCode() / 100 != 2) {
                log.warn("Announcement to " + coordinatorSlotsUrl + " failed: " + response.getStatusText());
            }
            slots = codec.fromJson(response.getResponseBody());
        }
        catch (InterruptedException e) {
            throw e;
        }
        catch (Exception e) {
            if (e.getCause() instanceof ConnectException) {
                log.warn("Could not connect to coordinator at " + coordinatorSlotsUrl);
            }
            else {
                log.warn(e, "Error announcing status to " + coordinatorSlotsUrl);
            }
            return;
        }

        ImmutableList.Builder<URI> discoveryServers = ImmutableList.builder();
        for (SlotStatusRepresentation slot : slots) {
            URI discoveryUri = getDiscoveryUri(slot);
            if (discoveryUri != null) {
                discoveryServers.add(discoveryUri);
            }
        }
        this.discoveryServers.set(discoveryServers.build());
    }

    private URI getDiscoveryUri(SlotStatusRepresentation slot)
    {
        BinarySpec binarySpec = BinarySpec.valueOf(slot.getBinary());
        if (!"com.proofpoint.discovery".equals(binarySpec.getGroupId()) || !"discovery-server".equals(binarySpec.getArtifactId())) {
            return null;
        }

        int port = loadDiscoveryHttpPort(ConfigSpec.valueOf(slot.getConfig()));
        if (port > 0) {
            try {
                return new URI("http", null, slot.getSelf().getHost(), port, null, null, null);
            }
            catch (URISyntaxException e) {
                // host announced self host is most likely invalid
            }
        }
        return null;
    }

    private int loadDiscoveryHttpPort(ConfigSpec configSpec)
    {
        InputStream input = null;
        try {
            InputSupplier<? extends InputStream> configFile = configurationRepository.getConfigFile(environment, configSpec.getComponent(), configSpec.getVersion(), configSpec.getPool(), "etc/config.properties");
            input = configFile.getInput();

            // load the http server port from the config file
            Properties properties = new Properties();
            properties.load(input);
            String portString = properties.getProperty("http-server.http.port");
            if (portString != null) {
                return Integer.parseInt(portString);
            }
        }
        catch (Exception ignored) {
        }
        finally {
            Closeables.closeQuietly(input);
        }

        return DEFAULT_DISCOVERY_PORT;
    }
}
