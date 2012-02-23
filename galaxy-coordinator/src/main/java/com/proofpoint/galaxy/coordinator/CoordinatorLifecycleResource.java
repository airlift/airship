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
import com.proofpoint.galaxy.shared.SlotLifecycleState;
import com.proofpoint.galaxy.shared.SlotStatus;

import javax.ws.rs.HeaderParam;
import javax.ws.rs.PUT;
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
import static com.proofpoint.galaxy.coordinator.Strings.shortestUniquePrefix;
import static com.proofpoint.galaxy.shared.SlotLifecycleState.UNKNOWN;
import static com.proofpoint.galaxy.shared.VersionsUtil.GALAXY_SLOTS_VERSION_HEADER;
import static com.proofpoint.galaxy.shared.SlotStatusRepresentation.fromSlotStatusWithShortIdPrefixSize;
import static com.proofpoint.galaxy.shared.VersionsUtil.createSlotsVersion;

@Path("/v1/slot/lifecycle")
public class CoordinatorLifecycleResource
{
    private final Coordinator coordinator;

    @Inject
    public CoordinatorLifecycleResource(Coordinator coordinator)
    {
        Preconditions.checkNotNull(coordinator, "coordinator must not be null");

        this.coordinator = coordinator;
    }

    @PUT
    @Produces(MediaType.APPLICATION_JSON)
    public Response setState(String newState,
            @Context UriInfo uriInfo,
            @HeaderParam(GALAXY_SLOTS_VERSION_HEADER) String expectedSlotsVersion)
    {
        Preconditions.checkNotNull(newState, "newState must not be null");

        SlotLifecycleState state = SlotLifecycleState.lookup(newState);
        if (state == null || state == UNKNOWN) {
            return Response.status(Response.Status.BAD_REQUEST).build();
        }

        // build filter
        List<UUID> uuids = Lists.transform(coordinator.getAllSlotStatus(), SlotStatus.uuidGetter());
        Predicate<SlotStatus> slotFilter = SlotFilterBuilder.build(uriInfo, true, uuids);

        // set slot state
        List<SlotStatus> results = coordinator.setState(state, slotFilter, expectedSlotsVersion);

        // build response
        int prefixSize = shortestUniquePrefix(transform(uuids, toStringFunction()), MIN_PREFIX_SIZE);
        return Response.ok(transform(results, fromSlotStatusWithShortIdPrefixSize(prefixSize)))
                .header(GALAXY_SLOTS_VERSION_HEADER, createSlotsVersion(results))
                .build();
     }
}
