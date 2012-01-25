package com.proofpoint.galaxy.configuration;

import com.proofpoint.galaxy.shared.ConfigUtils;

import javax.inject.Inject;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.core.StreamingOutput;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;

import static com.google.common.io.Files.newInputStreamSupplier;

@Path("/v1/config/{environment}/{type}/{pool}/{version}")
public class ConfigurationResource
{
    private final GitConfigurationStore configurationStore;

    @Inject
    public ConfigurationResource(GitConfigurationStore configStore)
    {
        this.configurationStore = configStore;
    }

    @GET
    public Response getConfigMap(
            @PathParam("environment") final String environment,
            @PathParam("type") final String type,
            @PathParam("pool") final String pool,
            @PathParam("version") final String version)
    {
        final File configDir = configurationStore.getConfigDir(environment, type, version, pool);
        if (configDir == null) {
            return Response.status(Status.NOT_FOUND).build();
        }

        return Response.ok(new StreamingOutput()
        {
            @Override
            public void write(OutputStream output)
                    throws IOException, WebApplicationException
            {
                ConfigUtils.packConfig(configDir, output);
            }
        }).build();
    }

    @GET
    @Path("{path: .*}")
    public Response getConfigFile(@PathParam("environment") String environment,
            @PathParam("type") String type,
            @PathParam("pool") String pool,
            @PathParam("version") String version,
            @PathParam("path") String path)
    {
        File configFile = configurationStore.getConfigFile(environment, type, version, pool, path);
        if (configFile == null) {
            return Response.status(Status.NOT_FOUND).build();
        }
        return Response.ok(new InputSupplierStreamingOutput(newInputStreamSupplier(configFile))).build();
    }
}
