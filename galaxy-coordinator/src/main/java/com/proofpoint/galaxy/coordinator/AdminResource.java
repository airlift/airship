package com.proofpoint.galaxy.coordinator;

import com.google.common.base.Throwables;
import com.google.inject.Inject;
import com.ning.http.client.AsyncHttpClient;
import com.proofpoint.galaxy.shared.AgentStatus;

import javax.ws.rs.DELETE;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.core.UriInfo;
import java.util.UUID;

@Path("/v1/admin/")
public class AdminResource
{
    private final Coordinator coordinator;
    private final AsyncHttpClient httpClient = new AsyncHttpClient();

    @Inject
    public AdminResource(Coordinator coordinator)
    {
        this.coordinator = coordinator;
    }

    @DELETE
    @Path("/agent/{agentId: [a-z0-9]+}")
    public Response deleteAgent(UUID agentId, @Context UriInfo uriInfo)
    {
        if (coordinator.removeAgent(agentId)) {
            return Response.ok().build();
        }
        return Response.status(Status.NOT_FOUND).build();
    }
}
