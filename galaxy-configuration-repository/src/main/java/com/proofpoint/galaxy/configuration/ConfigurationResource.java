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

    @Inject
    public ConfigurationResource(GitConfigurationRepository configRepository)
    {
        this.configurationRepository = configRepository;
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
        return Response.ok(new InputSupplierStreamingOutput(configFile)).build();
    }
}
