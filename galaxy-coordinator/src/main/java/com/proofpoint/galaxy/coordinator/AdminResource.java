package com.proofpoint.galaxy.coordinator;

import com.google.common.base.Predicate;
import com.google.common.collect.Lists;
import com.google.inject.Inject;
import com.proofpoint.galaxy.shared.AgentStatus;
import com.proofpoint.galaxy.shared.CoordinatorStatus;
import com.proofpoint.galaxy.shared.Repository;
import com.proofpoint.galaxy.shared.SlotStatus;

import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.core.UriInfo;
import java.util.List;
import java.util.UUID;

import static com.google.common.collect.Collections2.transform;
import static com.proofpoint.galaxy.shared.AgentStatusRepresentation.fromAgentStatus;
import static com.proofpoint.galaxy.shared.CoordinatorStatusRepresentation.fromCoordinatorStatus;
import static com.proofpoint.galaxy.shared.VersionsUtil.GALAXY_AGENTS_VERSION_HEADER;
import static com.proofpoint.galaxy.shared.VersionsUtil.createAgentsVersion;

@Path("/v1/admin/")
public class AdminResource
{
    private final Coordinator coordinator;
    private final Repository repository;

    @Inject
    public AdminResource(Coordinator coordinator, Repository repository)
    {
        this.coordinator = coordinator;
        this.repository = repository;
    }

    @GET
    @Path("/coordinator")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getAllCoordinators(@Context UriInfo uriInfo)
    {
        Predicate<CoordinatorStatus> coordinatorPredicate = CoordinatorFilterBuilder.build(uriInfo);
        List<CoordinatorStatus> coordinators = coordinator.getCoordinators(coordinatorPredicate);
        return Response.ok(transform(coordinators, fromCoordinatorStatus())).build();
    }

    @POST
    @Path("/coordinator")
    @Produces(MediaType.APPLICATION_JSON)
    public Response provisionCoordinator(
            CoordinatorProvisioningRepresentation provisioning,
            @Context UriInfo uriInfo)
            throws Exception
    {
        List<CoordinatorStatus> coordinators = coordinator.provisionCoordinators(
                provisioning.getCoordinatorConfig(),
                provisioning.getCoordinatorCount(),
                provisioning.getInstanceType(),
                provisioning.getAvailabilityZone(),
                provisioning.getAmi(),
                provisioning.getKeyPair(),
                provisioning.getSecurityGroup());

        return Response.ok(transform(coordinators, fromCoordinatorStatus())).build();
    }

    @GET
    @Path("/agent")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getAllAgents(@Context UriInfo uriInfo)
    {
        List<SlotStatus> allSlotStatus = coordinator.getAllSlotStatus();
        List<UUID> uuids = Lists.transform(allSlotStatus, SlotStatus.uuidGetter());
        Predicate<AgentStatus> agentPredicate = AgentFilterBuilder.build(uriInfo, uuids, false, repository);

        List<AgentStatus> agents = coordinator.getAgents(agentPredicate);

        return Response.ok(transform(agents, fromAgentStatus()))
                .header(GALAXY_AGENTS_VERSION_HEADER, createAgentsVersion(agents))
                .build();
    }

    @POST
    @Path("/agent")
    @Produces(MediaType.APPLICATION_JSON)
    public Response provisionAgent(
            AgentProvisioningRepresentation provisioning,
            @Context UriInfo uriInfo)
            throws Exception
    {
        List<AgentStatus> agents = coordinator.provisionAgents(
                provisioning.getAgentConfig(),
                provisioning.getAgentCount(),
                provisioning.getInstanceType(),
                provisioning.getAvailabilityZone(),
                provisioning.getAmi(),
                provisioning.getKeyPair(),
                provisioning.getSecurityGroup());

        return Response.ok(transform(agents, fromAgentStatus())).build();
    }

    @DELETE
    @Path("/agent/{agentId: [a-z0-9-]+}")
    public Response terminateAgent(String agentId, @Context UriInfo uriInfo)
    {
        if (coordinator.terminateAgent(agentId) != null) {
            return Response.ok().build();
        }
        return Response.status(Status.NOT_FOUND).build();
    }
}
