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
package io.airlift.airship.coordinator;

import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.collect.Lists;
import com.google.inject.Inject;
import io.airlift.airship.shared.Repository;
import io.airlift.airship.shared.SlotLifecycleState;
import io.airlift.airship.shared.SlotStatus;

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
import static io.airlift.airship.shared.SlotLifecycleState.UNKNOWN;
import static io.airlift.airship.shared.SlotStatusRepresentation.fromSlotStatus;
import static io.airlift.airship.shared.VersionsUtil.AIRSHIP_SLOTS_VERSION_HEADER;
import static io.airlift.airship.shared.VersionsUtil.createSlotsVersion;

@Path("/v1/slot/lifecycle")
public class CoordinatorLifecycleResource
{
    private final Coordinator coordinator;
    private final Repository repository;

    @Inject
    public CoordinatorLifecycleResource(Coordinator coordinator, Repository repository)
    {
        Preconditions.checkNotNull(coordinator, "coordinator must not be null");
        Preconditions.checkNotNull(repository, "repository is null");

        this.coordinator = coordinator;
        this.repository = repository;
    }

    @PUT
    @Produces(MediaType.APPLICATION_JSON)
    public Response setState(String newState,
            @Context UriInfo uriInfo,
            @HeaderParam(AIRSHIP_SLOTS_VERSION_HEADER) String expectedSlotsVersion)
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
        return Response.ok(transform(results, fromSlotStatus(coordinator.getAllSlotStatus(), repository)))
                .header(AIRSHIP_SLOTS_VERSION_HEADER, createSlotsVersion(results))
                .build();
     }
}
