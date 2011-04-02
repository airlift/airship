package com.proofpoint.galaxy.coordinator;

import com.google.common.base.Preconditions;
import com.google.inject.Inject;
import com.proofpoint.galaxy.AgentStatus;
import com.proofpoint.galaxy.AgentStatusRepresentation;

import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.UUID;

@Path("/v1/announce/{agentId}")
public class AnnounceResource
{
    private final Coordinator store;

    @Inject
    public AnnounceResource(Coordinator store)
    {
        this.store = store;
    }

    @PUT
    @Consumes(MediaType.APPLICATION_JSON)
    public Response updateAgentStatus(@PathParam("agentId") UUID agentId, AgentStatusRepresentation statusRepresentation)
    {
        Preconditions.checkNotNull(agentId, "agentId must not be null");
        Preconditions.checkNotNull(statusRepresentation, "statusRepresentation must not be null");

        if (!agentId.equals(statusRepresentation.getAgentId())) {
            return Response.status(Response.Status.BAD_REQUEST).build();
        }

        AgentStatus status = statusRepresentation.toAgentStatus();
        store.updateAgentStatus(status);
        return Response.noContent().build();
    }

    @DELETE
    public Response removeAgentStatus(@PathParam("agentId") UUID agentId)
    {
        Preconditions.checkNotNull(agentId, "agentId must not be null");

        if (!store.removeAgentStatus(agentId)) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        return Response.noContent().build();
    }
}
