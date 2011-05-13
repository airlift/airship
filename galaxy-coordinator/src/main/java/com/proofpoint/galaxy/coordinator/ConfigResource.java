package com.proofpoint.galaxy.coordinator;

import com.google.common.io.ByteStreams;
import com.google.common.io.InputSupplier;
import com.proofpoint.galaxy.shared.ConfigSpec;

import javax.inject.Inject;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.core.StreamingOutput;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.util.Map;

@Path("/v1/config/{environment}/{type}/{pool}/{version}")
public class ConfigResource
{
    private final LocalConfigRepository localConfigRepository;

    @Inject
    public ConfigResource(LocalConfigRepository localConfigRepository)
    {
        this.localConfigRepository = localConfigRepository;
    }

    @GET
    @Path("config-map.json")
    public Response getConfigMap(@PathParam("environment") String environment,
            @PathParam("type") String type,
            @PathParam("pool") String pool,
            @PathParam("version") String version)
    {
        ConfigSpec configSpec = new ConfigSpec(environment, type, version, pool);
        Map<String, URI> configMap = localConfigRepository.getConfigMap(configSpec);
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
        ConfigSpec configSpec = new ConfigSpec(environment, type, version, pool);
        InputSupplier<FileInputStream> configFile = localConfigRepository.getConfigFile(configSpec, path);
        if (configFile == null) {
            return Response.status(Status.NOT_FOUND).build();
        }

        return Response.ok(new InputSupplierStreamingOutput(configFile)).build();
    }

    private static class InputSupplierStreamingOutput implements StreamingOutput
    {
        private final InputSupplier<? extends InputStream> inputSupplier;

        public InputSupplierStreamingOutput(InputSupplier<? extends InputStream> inputSupplier)
        {
            this.inputSupplier = inputSupplier;
        }

        public void write(OutputStream output)
                throws IOException, WebApplicationException
        {
            ByteStreams.copy(inputSupplier, output);
        }
    }
}
