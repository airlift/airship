package com.proofpoint.galaxy.coordinator;

import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.collect.Lists;
import com.google.inject.Inject;
import com.proofpoint.galaxy.shared.SlotStatus;
import com.proofpoint.galaxy.shared.VersionsUtil;

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
import static com.proofpoint.galaxy.coordinator.CoordinatorSlotResource.MIN_PREFIX_SIZE;
import static com.proofpoint.galaxy.coordinator.StringFunctions.toStringFunction;
import static com.proofpoint.galaxy.shared.VersionsUtil.GALAXY_SLOTS_VERSION_HEADER;
import static com.proofpoint.galaxy.shared.VersionsUtil.checkSlotsVersion;
import static com.proofpoint.galaxy.shared.SlotStatusRepresentation.fromSlotStatusWithShortIdPrefixSize;
import static java.lang.Math.max;

@Path("/v1/slot/expected-state")
public class ExpectedStateResource
{
    private final Coordinator coordinator;

    @Inject
    public ExpectedStateResource(Coordinator coordinator)
    {
        Preconditions.checkNotNull(coordinator, "coordinator must not be null");

        this.coordinator = coordinator;
    }

    @DELETE
    @Produces(MediaType.APPLICATION_JSON)
    public Response terminateSlots(@Context UriInfo uriInfo,
            @HeaderParam(GALAXY_SLOTS_VERSION_HEADER) String expectedSlotsVersion)
    {
        List<SlotStatus> allSlotStatus = coordinator.getAllSlotStatus();
        checkSlotsVersion(expectedSlotsVersion, allSlotStatus);

        List<UUID> uuids = Lists.transform(allSlotStatus, SlotStatus.uuidGetter());
        int prefixSize = MIN_PREFIX_SIZE;
        if (!uuids.isEmpty()) {
            prefixSize = max(prefixSize, Strings.shortestUniquePrefix(transform(uuids, toStringFunction())));
        }

        Predicate<SlotStatus> slotFilter = SlotFilterBuilder.build(uriInfo, true, uuids);

        List<SlotStatus> result = coordinator.resetExpectedState(slotFilter);

        return Response.ok(transform(result, fromSlotStatusWithShortIdPrefixSize(prefixSize)))
                .header(GALAXY_SLOTS_VERSION_HEADER, VersionsUtil.createSlotsVersion(coordinator.getAllSlotStatus()))
                .build();
    }
}
