package com.proofpoint.galaxy.coordinator;

import com.google.common.base.Throwables;
import com.google.inject.Inject;
import com.ning.http.client.AsyncHttpClient;
import com.proofpoint.galaxy.shared.AgentStatus;

import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.core.UriInfo;

@Path("/v1/admin/slot")
public class AdminResource
{
    private final Coordinator coordinator;
    private final AsyncHttpClient httpClient = new AsyncHttpClient();

    @Inject
    public AdminResource(Coordinator coordinator)
    {
        this.coordinator = coordinator;
    }

    @POST
    public Response addSlot(@Context UriInfo uriInfo)
    {
        for (AgentStatus agentStatus : coordinator.getAllAgentStatus()) {
            createSlot(agentStatus);
        }
        return Response.noContent().build();
    }

    private void createSlot(AgentStatus agentStatus)
    {
        try {
            com.ning.http.client.Response response = httpClient.preparePost(agentStatus.getUri() + "/v1/slot")
                    .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON)
                    .execute()
                    .get();

            if (response.getStatusCode() != Status.CREATED.getStatusCode()) {
                throw new RuntimeException("Assignment Failed with " + response.getStatusCode() + " " + response.getStatusText());
            }
        }
        catch (Exception e) {
            throw Throwables.propagate(e);
        }
    }
}
