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
import java.util.UUID;

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

        List<UUID> uuids = Lists.transform(agents, AgentStatus.uuidGetter());

        int prefixSize = MIN_PREFIX_SIZE;
        if (!uuids.isEmpty()) {
            prefixSize = max(prefixSize, Strings.shortestUniquePrefix(transform(uuids, toStringFunction())));
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
        // TODO: provision all agents in one call
        List<AgentStatus> agents = newArrayList();
        for (int i = 0; i < count; i++) {
            UUID agentId = UUID.randomUUID();
            AgentStatus agentStatus = new AgentStatus(agentId,
                    AgentLifecycleState.PROVISIONING,
                    URI.create("http://localhost"),
                    (provisioning.getAvailabilityZone() == null ? "unknown" : provisioning.getAvailabilityZone()) + "/" + agentId,
                    provisioning.getInstanceType(),
                    ImmutableList.<SlotStatus>of());
            provisioner.provisionAgent(agentId.toString(), provisioning.getInstanceType(), provisioning.getAvailabilityZone());
            coordinator.updateAgentStatus(agentStatus);
            agents.add(agentStatus);
        }

        List<UUID> uuids = Lists.transform(coordinator.getAllAgents(), AgentStatus.uuidGetter());

        int prefixSize = MIN_PREFIX_SIZE;
        if (!uuids.isEmpty()) {
            prefixSize = max(prefixSize, Strings.shortestUniquePrefix(transform(uuids, toStringFunction())));
        }

        return Response.ok(transform(agents, fromAgentStatusWithShortIdPrefixSize(prefixSize))).build();
    }

    @DELETE
    @Path("/agent/{agentId: [a-z0-9-]+}")
    public Response deleteAgent(UUID agentId, @Context UriInfo uriInfo)
    {
        if (coordinator.removeAgent(agentId)) {
            return Response.ok().build();
        }
        return Response.status(Status.NOT_FOUND).build();
    }
}
