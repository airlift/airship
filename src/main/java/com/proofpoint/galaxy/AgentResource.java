package com.proofpoint.galaxy;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableList.Builder;
import com.google.inject.Inject;

import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;
import java.util.List;
import java.util.UUID;

@Path("/v1/agent")
public class AgentResource
{
    private final ConsoleStore store;

    @Inject
    public AgentResource(ConsoleStore store)
    {
        this.store = store;
    }

    @Path("{agentId}")
    @PUT
    @Produces(MediaType.APPLICATION_JSON)
    public Response updateAgentStatus(@PathParam("agentId") UUID agentId, AgentStatusRepresentation statusRepresentation, @Context UriInfo uriInfo)
    {
        Preconditions.checkNotNull(agentId, "agentId must not be null");
        Preconditions.checkNotNull(statusRepresentation, "statusRepresentation must not be null");

        if (!agentId.toString().equals(statusRepresentation.getAgentId()) ) {
            return Response.status(Response.Status.BAD_REQUEST).build();
        }

        AgentStatus status = statusRepresentation.toAgentStatus();
        store.updateAgentStatus(status);
        return Response.noContent().build();
    }

    @Path("{agentId}")
    @DELETE
    public Response removeAgentStatus(@PathParam("agentId") UUID agentId)
    {
        Preconditions.checkNotNull(agentId, "agentId must not be null");

        if (!store.removeAgentStatus(agentId)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        return Response.noContent().build();
    }


    @Path("{agentId}")
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response getAgentStatus(@PathParam("agentId") UUID agentId, @Context UriInfo uriInfo)
    {
        Preconditions.checkNotNull(agentId, "agentId must not be null");

        AgentStatus agentStatus = store.getAgentStatus(agentId);
        if (agentStatus == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        return Response.ok(AgentStatusRepresentation.from(agentStatus, uriInfo)).build();
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response getAllAgentStatus(@Context UriInfo uriInfo)
    {
        List<AgentStatus> allAgentStatus = store.getAllAgentStatus();
        Builder<AgentStatusRepresentation> builder = ImmutableList.builder();
        for (AgentStatus agentStatus : allAgentStatus) {
            builder.add(AgentStatusRepresentation.from(agentStatus, uriInfo));
        }
        return Response.ok(builder.build()).build();
    }
}
