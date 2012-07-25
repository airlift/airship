/**
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.proofpoint.galaxy.coordinator;

import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.collect.Iterables;
import com.google.inject.Inject;
import com.proofpoint.galaxy.shared.AgentStatus;
import com.proofpoint.galaxy.shared.Assignment;
import com.proofpoint.galaxy.shared.AssignmentRepresentation;
import com.proofpoint.galaxy.shared.Repository;
import com.proofpoint.galaxy.shared.SlotStatus;

import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;
import java.util.List;
import java.util.UUID;

import static com.google.common.collect.Lists.transform;
import static com.proofpoint.galaxy.shared.AgentStatus.idGetter;
import static com.proofpoint.galaxy.shared.SlotStatus.uuidGetter;
import static com.proofpoint.galaxy.shared.SlotStatusRepresentation.fromSlotStatus;
import static com.proofpoint.galaxy.shared.VersionsUtil.GALAXY_AGENTS_VERSION_HEADER;
import static com.proofpoint.galaxy.shared.VersionsUtil.GALAXY_SLOTS_VERSION_HEADER;
import static com.proofpoint.galaxy.shared.VersionsUtil.checkAgentsVersion;
import static com.proofpoint.galaxy.shared.VersionsUtil.createSlotsVersion;

@Path("/v1/slot")
public class CoordinatorSlotResource
{
    public static final int MIN_PREFIX_SIZE = 4;

    private final Coordinator coordinator;
    private final Repository repository;

    @Inject
    public CoordinatorSlotResource(Coordinator coordinator, Repository repository)
    {
        Preconditions.checkNotNull(coordinator, "coordinator must not be null");
        Preconditions.checkNotNull(repository, "repository is null");

        this.coordinator = coordinator;
        this.repository = repository;
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response getAllSlots(@Context UriInfo uriInfo)
    {
        // build filter
        List<UUID> uuids = transform(coordinator.getAllSlotStatus(), uuidGetter());
        Predicate<SlotStatus> slotFilter = SlotFilterBuilder.build(uriInfo, false, uuids);

        // select slots
        List<SlotStatus> slots = coordinator.getAllSlotsStatus(slotFilter);

        // build response
        return Response.ok(Iterables.transform(slots, fromSlotStatus(coordinator.getAllSlotStatus(), repository)))
                .header(GALAXY_SLOTS_VERSION_HEADER, createSlotsVersion(slots))
                .build();
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response install(
            AssignmentRepresentation assignmentRepresentation,
            @DefaultValue("1") @QueryParam("limit") int limit,
            @Context UriInfo uriInfo,
            @HeaderParam(GALAXY_AGENTS_VERSION_HEADER) String expectedAgentsVersion)
    {
        Preconditions.checkNotNull(assignmentRepresentation, "assignmentRepresentation must not be null");
        Preconditions.checkArgument(limit > 0, "limit must be at least 1");

        Assignment assignment = assignmentRepresentation.toAssignment();

        // select the target agents
        Predicate<AgentStatus> agentFilter = AgentFilterBuilder.build(uriInfo,
                transform(coordinator.getAgents(), idGetter()),
                transform(coordinator.getAllSlotStatus(), uuidGetter()),
                false,
                repository);
        List<AgentStatus> agents = coordinator.getAgents(agentFilter);

        // verify the expected status of agents
        checkAgentsVersion(expectedAgentsVersion, agents);

        // install the software
        List<SlotStatus> slots = coordinator.install(agentFilter, limit, assignment);

        // calculate unique prefix size with the new slots included
        return Response.ok(transform(slots, fromSlotStatus(coordinator.getAllSlotStatus(), repository)))
                .header(GALAXY_SLOTS_VERSION_HEADER, createSlotsVersion(slots))
                .build();
    }

    @DELETE
    @Produces(MediaType.APPLICATION_JSON)
    public Response terminateSlots(@Context UriInfo uriInfo,
            @HeaderParam(GALAXY_SLOTS_VERSION_HEADER) String expectedSlotsVersion)
    {
        // build filter
        List<UUID> uuids = transform(coordinator.getAllSlotStatus(), uuidGetter());
        Predicate<SlotStatus> slotFilter = SlotFilterBuilder.build(uriInfo, true, uuids);

        // terminate slots
        List<SlotStatus> result = coordinator.terminate(slotFilter, expectedSlotsVersion);

        // build response
        return Response.ok(transform(result, fromSlotStatus(coordinator.getAllSlotStatus(), repository)))
                .header(GALAXY_SLOTS_VERSION_HEADER, createSlotsVersion(result))
                .build();
    }
}
