package com.proofpoint.galaxy.coordinator;

import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.collect.Lists;
import com.google.inject.Inject;
import com.proofpoint.galaxy.shared.Repository;
import com.proofpoint.galaxy.shared.SlotStatus;

import javax.ws.rs.DELETE;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;
import java.util.List;
import java.util.UUID;

import static com.google.common.collect.Collections2.transform;
import static com.proofpoint.galaxy.shared.SlotStatus.uuidGetter;
import static com.proofpoint.galaxy.shared.VersionsUtil.GALAXY_SLOTS_VERSION_HEADER;
import static com.proofpoint.galaxy.shared.SlotStatusRepresentation.fromSlotStatus;
import static com.proofpoint.galaxy.shared.VersionsUtil.createSlotsVersion;

@Path("/v1/slot/expected-state")
public class ExpectedStateResource
{
    private final Coordinator coordinator;
    private final Repository repository;

    @Inject
    public ExpectedStateResource(Coordinator coordinator, Repository repository)
    {
        Preconditions.checkNotNull(coordinator, "coordinator must not be null");
        Preconditions.checkNotNull(repository, "repository is null");

        this.coordinator = coordinator;
        this.repository = repository;
    }

    @DELETE
    @Produces(MediaType.APPLICATION_JSON)
    public Response terminateSlots(@Context UriInfo uriInfo,
            @HeaderParam(GALAXY_SLOTS_VERSION_HEADER) String expectedSlotsVersion)
    {
        // build filter
        List<UUID> uuids = Lists.transform(coordinator.getAllSlotStatus(), uuidGetter());
        Predicate<SlotStatus> slotFilter = SlotFilterBuilder.build(uriInfo, true, uuids);

        // reset slots expected state
        List<SlotStatus> result = coordinator.resetExpectedState(slotFilter, expectedSlotsVersion);

        // build response
        return Response.ok(transform(result, fromSlotStatus(coordinator.getAllSlotStatus(), repository)))
                .header(GALAXY_SLOTS_VERSION_HEADER, createSlotsVersion(result))
                .build();
    }
}
