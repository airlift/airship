package io.airlift.airship.coordinator;

import com.google.common.base.Preconditions;
import com.google.common.io.Resources;
import io.airlift.airship.shared.Repository;
import io.airlift.airship.shared.WritableRepository;

import javax.inject.Inject;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;

import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;

/**
 */
@Path("/v1/config")
public class CoordinatorConfigProxyResource
{
    private final Repository repository;
    private final WritableRepository writableRepository;

    @Inject
    public CoordinatorConfigProxyResource(
            Repository repository
    )
    {
        this.repository = Preconditions.checkNotNull(repository, "repository was null");
        this.writableRepository = (WritableRepository) (repository instanceof WritableRepository ? repository : null);
    }

    @GET
    @Produces(MediaType.APPLICATION_OCTET_STREAM)
    public Response getConfigs(
            @QueryParam("identifier") String identifier
    )
    {
        final URL url;
        try {
            url = repository.configToHttpUri(identifier).toURL();
        }
        catch (MalformedURLException e) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        return Response.ok(Resources.newInputStreamSupplier(url)).build();
    }

    @PUT
    @Consumes(MediaType.APPLICATION_OCTET_STREAM)
    public Response createConfigs(
            UriInfo info,
            @QueryParam("identifier") String identifier,
            InputStream configPayload
    )
    {
        if (writableRepository == null) {
            return Response.status(405).build();
        }

        if (writableRepository.configToHttpUri(identifier) != null) {
            return Response.status(Response.Status.BAD_REQUEST).build();
        }

        writableRepository.put(identifier, configPayload);
        return Response.created(info.getRequestUri()).build();
    }
}
