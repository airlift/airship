package com.proofpoint.galaxy.coordinator;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.inject.Inject;
import com.proofpoint.galaxy.shared.AgentLifecycleState;
import com.proofpoint.galaxy.shared.AgentStatus;
import com.proofpoint.galaxy.shared.SlotStatus;

import javax.ws.rs.DELETE;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.core.UriInfo;
import java.net.URI;
import java.util.List;

import static com.google.common.collect.Collections2.transform;
import static com.google.common.collect.Lists.newArrayList;
import static com.proofpoint.galaxy.coordinator.StringFunctions.toStringFunction;
import static com.proofpoint.galaxy.shared.AgentStatusRepresentation.fromAgentStatusWithShortIdPrefixSize;
import static java.lang.Math.max;

@Path("/v1/admin/")
public class AdminResource
{
    private final Coordinator coordinator;
    private final AwsProvisioner provisioner;
    public static final int MIN_PREFIX_SIZE = 4;

    @Inject
    public AdminResource(Coordinator coordinator, AwsProvisioner provisioner)
    {
        this.coordinator = coordinator;
        this.provisioner = provisioner;
    }

    @GET
    @Path("/agent")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getAllSlotsStatus(@Context UriInfo uriInfo)
    {
        List<AgentStatus> agents = coordinator.getAllAgentStatus();

        List<String> agentIds = Lists.transform(agents, AgentStatus.idGetter());

        int prefixSize = MIN_PREFIX_SIZE;
        if (!agentIds.isEmpty()) {
            prefixSize = max(prefixSize, Strings.shortestUniquePrefix(transform(agentIds, toStringFunction())));
        }

        return Response.ok(transform(agents, fromAgentStatusWithShortIdPrefixSize(prefixSize))).build();
    }

    @POST
    @Path("/agent")
    @Produces(MediaType.APPLICATION_JSON)
    public Response provisionAgent(
            AgentProvisioningRepresentation provisioning,
            @DefaultValue("1") @QueryParam("count") int count,
            @Context UriInfo uriInfo)
            throws Exception
    {
        List<Ec2Location> locations = provisioner.provisionAgents(count, provisioning.getInstanceType(), provisioning.getAvailabilityZone());

        List<AgentStatus> agents = newArrayList();
        for (Ec2Location location : locations) {
            AgentStatus agentStatus = new AgentStatus(
                    location.getInstanceId(),
                    AgentLifecycleState.PROVISIONING,
                    URI.create("http://localhost"),
                    location.toString(),
                    provisioning.getInstanceType(),
                    ImmutableList.<SlotStatus>of());
            coordinator.updateAgentStatus(agentStatus);
            agents.add(agentStatus);
        }

        List<String> agentIds = Lists.transform(coordinator.getAllAgents(), AgentStatus.idGetter());

        int prefixSize = MIN_PREFIX_SIZE;
        if (!agentIds.isEmpty()) {
            prefixSize = max(prefixSize, Strings.shortestUniquePrefix(transform(agentIds, toStringFunction())));
        }

        return Response.ok(transform(agents, fromAgentStatusWithShortIdPrefixSize(prefixSize))).build();
    }

    @DELETE
    @Path("/agent/{agentId: [a-z0-9-]+}")
    public Response deleteAgent(String agentId, @Context UriInfo uriInfo)
    {
        if (coordinator.removeAgent(agentId)) {
            return Response.ok().build();
        }
        return Response.status(Status.NOT_FOUND).build();
    }
}
