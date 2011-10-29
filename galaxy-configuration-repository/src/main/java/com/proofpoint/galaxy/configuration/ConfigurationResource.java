package com.proofpoint.galaxy.configuration;

import javax.inject.Inject;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import java.io.File;
import java.net.URI;
import java.util.Map;

import static com.google.common.io.Files.newInputStreamSupplier;

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
        File configFile = configurationRepository.getConfigFile(environment, type, version, pool, path);
        if (configFile == null) {
            return Response.status(Status.NOT_FOUND).build();
        }
        return Response.ok(new InputSupplierStreamingOutput(newInputStreamSupplier(configFile))).build();
    }
}
