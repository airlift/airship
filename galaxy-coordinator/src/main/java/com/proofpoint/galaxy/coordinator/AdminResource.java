package com.proofpoint.galaxy.coordinator;

import com.google.common.collect.ImmutableList;
import com.google.inject.Inject;
import com.proofpoint.galaxy.shared.AgentStatus;
import com.proofpoint.galaxy.shared.AgentStatusRepresentation;

import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.core.UriInfo;
import java.util.List;
import java.util.UUID;

@Path("/v1/admin/")
public class AdminResource
{
    private final Coordinator coordinator;

    @Inject
    public AdminResource(Coordinator coordinator)
    {
        this.coordinator = coordinator;
    }

    @GET
    @Path("/agent")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getAllSlotsStatus(@Context UriInfo uriInfo)
    {
        List<AgentStatus> agents = coordinator.getAllAgentStatus();
        ImmutableList.Builder<AgentStatusRepresentation> builder = ImmutableList.builder();
        for (AgentStatus agentStatus : agents) {
            builder.add(AgentStatusRepresentation.from(agentStatus));
        }
        return Response.ok(builder.build()).build();
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
