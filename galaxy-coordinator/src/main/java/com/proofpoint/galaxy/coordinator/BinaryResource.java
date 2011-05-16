package com.proofpoint.galaxy.coordinator;

import com.google.common.io.Resources;
import com.proofpoint.galaxy.shared.BinarySpec;

import javax.inject.Inject;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import java.io.File;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;

import static com.proofpoint.galaxy.shared.FileUtils.newFile;

@Path("/v1/binary/")
public class BinaryResource
{
    private final URI localMavenRepo;

    @Inject
    public BinaryResource(CoordinatorConfig config)
    {
        if (config.isLocalMavenRepositoryEnabled()) {
            // add the local maven repository first
            File localMavenRepo = newFile(System.getProperty("user.home"), ".m2", "repository");
            if (localMavenRepo.isDirectory()) {
                this.localMavenRepo = localMavenRepo.toURI();
            } else {
                this.localMavenRepo = null;
            }
        } else {
            this.localMavenRepo = null;
        }
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
            binaryUrl = getBinaryUrl(binarySpec);
        }
        catch (Exception ignored) {
        }
        if (binaryUrl == null) {
            return Response.status(Status.NOT_FOUND).build();
        }

        return Response.ok(new InputSupplierStreamingOutput(Resources.newInputStreamSupplier(binaryUrl))).build();
    }

    private URL getBinaryUrl(BinarySpec binarySpec)
    {
        URI binaryUri = MavenBinaryRepository.getBinaryUri(binarySpec, localMavenRepo);
        if (binaryUri != null) {
            try {
                return binaryUri.toURL();
            }
            catch (MalformedURLException ignored) {
            }
        }
        return null;
    }
}
