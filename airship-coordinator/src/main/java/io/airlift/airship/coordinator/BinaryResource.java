package io.airlift.airship.coordinator;

import com.google.common.io.Resources;
import io.airlift.airship.shared.MavenCoordinates;
import io.airlift.airship.shared.Repository;

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
    private final Repository repository;

    @Inject
    public BinaryResource(Repository repository)
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
        MavenCoordinates coordinates = new MavenCoordinates(groupId, artifactId, version, packaging, classifier, null);

        URL binaryUrl = null;
        try {
            binaryUrl = repository.binaryToHttpUri(coordinates.toGAV()).toURL();
        }
        catch (MalformedURLException e) {
        }

        if (binaryUrl == null) {
            return Response.status(Status.NOT_FOUND).build();
        }

        return Response.ok(new InputSupplierStreamingOutput(Resources.newInputStreamSupplier(binaryUrl))).build();
    }
}
