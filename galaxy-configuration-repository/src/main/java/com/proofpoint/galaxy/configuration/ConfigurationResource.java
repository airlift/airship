package com.proofpoint.galaxy.configuration;

import com.google.common.base.Charsets;
import com.google.common.base.Joiner;
import com.google.common.io.ByteStreams;
import com.google.common.io.InputSupplier;

import javax.inject.Inject;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import java.io.InputStream;
import java.net.URI;
import java.util.List;
import java.util.Map;

@Path("/v1/config/{environment}/{type}/{pool}/{version}")
public class ConfigurationResource
{
    private final GitConfigurationRepository configurationRepository;
    private final DiscoverDiscovery discoverDiscovery;

    @Inject
    public ConfigurationResource(GitConfigurationRepository configRepository, DiscoverDiscovery discoverDiscovery)
    {
        this.configurationRepository = configRepository;
        this.discoverDiscovery = discoverDiscovery;
    }

    @GET
    @Path("config-map.json")
    public Response getConfigMap(@PathParam("environment") String environment,
            @PathParam("type") String type,
            @PathParam("pool") String pool,
            @PathParam("version") String version)
    {
        Map<String, URI> configMap = configurationRepository.getConfigMap(environment, type, version, pool);
        if (configMap == null) {
            return Response.status(Status.NOT_FOUND).build();
        }

        return Response.ok(configMap).build();
    }

    @GET
    @Path("{path: .*}")
    public Response getConfigFile(@PathParam("environment") String environment,
            @PathParam("type") String type,
            @PathParam("pool") String pool,
            @PathParam("version") String version,
            @PathParam("path") String path)
    {
        InputSupplier<? extends InputStream> configFile = configurationRepository.getConfigFile(environment, type, version, pool, path);
        if (configFile == null) {
            return Response.status(Status.NOT_FOUND).build();
        }

        // add discovery url to config.properties
        if (path.equals("etc/config.properties")) {
            StringBuilder additionalConfig = new StringBuilder();
            additionalConfig.append("#\n");
            additionalConfig.append("# Automatically provided by Galaxy Configuration Repository\n");
            List<URI> discoveryServers = discoverDiscovery.getDiscoveryServers();
            if (!discoveryServers.isEmpty()) {
                additionalConfig.append("discovery.uri=").append(Joiner.on(",").join(discoveryServers)).append("\n");
            }
            additionalConfig.append("\n");

            configFile = ByteStreams.join(configFile, ByteStreams.newInputStreamSupplier(additionalConfig.toString().getBytes(Charsets.UTF_8)));
        }

        return Response.ok(new InputSupplierStreamingOutput(configFile)).build();
    }
}
