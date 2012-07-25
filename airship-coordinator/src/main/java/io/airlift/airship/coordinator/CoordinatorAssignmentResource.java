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
import com.google.common.collect.Lists;
import com.google.inject.Inject;
import com.proofpoint.galaxy.shared.Repository;
import com.proofpoint.galaxy.shared.SlotStatus;
import com.proofpoint.galaxy.shared.UpgradeVersions;

import javax.ws.rs.Consumes;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;
import java.util.List;
import java.util.UUID;

import static com.google.common.collect.Collections2.transform;
import static com.proofpoint.galaxy.shared.VersionsUtil.GALAXY_SLOTS_VERSION_HEADER;
import static com.proofpoint.galaxy.shared.SlotStatusRepresentation.fromSlotStatus;
import static com.proofpoint.galaxy.shared.VersionsUtil.createSlotsVersion;

@Path("/v1/slot/assignment")
public class CoordinatorAssignmentResource
{
    private final Coordinator coordinator;
    private final Repository repository;

    @Inject
    public CoordinatorAssignmentResource(Coordinator coordinator, Repository repository)
    {
        Preconditions.checkNotNull(coordinator, "coordinator must not be null");
        Preconditions.checkNotNull(repository, "repository is null");

        this.coordinator = coordinator;
        this.repository = repository;
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response upgrade(UpgradeVersions upgradeVersions,
            @Context UriInfo uriInfo,
            @HeaderParam(GALAXY_SLOTS_VERSION_HEADER) String expectedSlotsVersion)
    {
        Preconditions.checkNotNull(upgradeVersions, "upgradeRepresentation must not be null");

        // build filter
        List<UUID> uuids = Lists.transform(coordinator.getAllSlotStatus(), SlotStatus.uuidGetter());
        Predicate<SlotStatus> slotFilter = SlotFilterBuilder.build(uriInfo, true, uuids);

        // upgrade slots
        List<SlotStatus> results = coordinator.upgrade(slotFilter, upgradeVersions, expectedSlotsVersion);

        // build response
        return Response.ok(transform(results, fromSlotStatus(coordinator.getAllSlotStatus(), repository)))
                .header(GALAXY_SLOTS_VERSION_HEADER, createSlotsVersion(results))
                .build();
    }
}
