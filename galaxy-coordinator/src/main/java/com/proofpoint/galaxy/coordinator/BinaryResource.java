package com.proofpoint.galaxy.coordinator;

import com.google.common.io.Resources;
import com.proofpoint.galaxy.shared.BinarySpec;

import javax.inject.Inject;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import java.net.MalformedURLException;
import java.net.URL;

@Path("/v1/binary/")
public class BinaryResource
{
    private final BinaryRepository repository;

    @Inject
    public BinaryResource(BinaryRepository repository)
    {
        this.repository = repository;
    }

    @GET
    @Path("{groupId}/{artifactId}/{version}/{packaging}")
    public Response getBinary(@PathParam("groupId") String groupId,
            @PathParam("artifactId") String artifactId,
            @PathParam("version") String version,
            @PathParam("packaging") String packaging)
    {
        return getBinary(groupId, artifactId, version, packaging, null);
    }

    @GET
    @Path("{groupId}/{artifactId}/{version}/{packaging}/{classifier}")
    public Response getBinary(@PathParam("groupId") String groupId,
            @PathParam("artifactId") String artifactId,
            @PathParam("version") String version,
            @PathParam("packaging") String packaging,
            @PathParam("classifier") String classifier)
    {
        BinarySpec binarySpec = new BinarySpec(groupId, artifactId, version, packaging, classifier);

        URL binaryUrl = null;
        try {
            binaryUrl = repository.getBinaryUri(binarySpec).toURL();
        }
        catch (MalformedURLException e) {
        }

        if (binaryUrl == null) {
            return Response.status(Status.NOT_FOUND).build();
        }

        return Response.ok(new InputSupplierStreamingOutput(Resources.newInputStreamSupplier(binaryUrl))).build();
    }
}
