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
import com.google.inject.Inject;
import com.proofpoint.galaxy.shared.SlotStatus;

import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;
import java.util.List;

import static com.google.common.collect.Collections2.transform;
import static com.proofpoint.galaxy.shared.SlotStatusRepresentation.fromSlotStatus;

@Path("/v1/slot")
public class CoordinatorSlotResource
{
    private final Coordinator coordinator;

    @Inject
    public CoordinatorSlotResource(Coordinator coordinator)
    {
        Preconditions.checkNotNull(coordinator, "coordinator must not be null");

        this.coordinator = coordinator;
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response getAllSlots(@Context UriInfo uriInfo)
    {
        Predicate<SlotStatus> slotFilter = SlotFilterBuilder.build(uriInfo, false);
        List<SlotStatus> result = coordinator.getAllSlotsStatus(slotFilter);

        return Response.ok(transform(result, fromSlotStatus())).build();
    }

    @DELETE
    @Produces(MediaType.APPLICATION_JSON)
    public Response terminateSlots(@Context UriInfo uriInfo)
    {
        Predicate<SlotStatus> slotFilter = SlotFilterBuilder.build(uriInfo, true);
        List<SlotStatus> result = coordinator.terminate(slotFilter);

        return Response.ok(transform(result, fromSlotStatus())).build();
    }
}
