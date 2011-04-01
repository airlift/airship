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
package com.proofpoint.galaxy.console;

import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.inject.Inject;
import com.proofpoint.galaxy.SlotStatus;
import com.proofpoint.galaxy.SlotStatusRepresentation;
import com.proofpoint.galaxy.agent.Installation;

import javax.validation.ConstraintViolation;
import javax.validation.Validation;
import javax.validation.Validator;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;
import java.util.Collection;
import java.util.List;
import java.util.Set;

@Path("/v1/slot/assignment")
public class ConsoleAssignmentResource
{
    private final Console console;
    private final BinaryRepository binaryRepository;
    private final ConfigRepository configRepository;

    @Inject
    public ConsoleAssignmentResource(Console console, BinaryRepository binaryRepository, ConfigRepository configRepository)
    {
        Preconditions.checkNotNull(console, "console must not be null");
        Preconditions.checkNotNull(configRepository, "repository is null");
        Preconditions.checkNotNull(binaryRepository, "binaryRepository is null");

        this.console = console;
        this.binaryRepository = binaryRepository;
        this.configRepository = configRepository;
    }

    @PUT
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response assign(AssignmentRepresentation assignmentRepresentation, @Context UriInfo uriInfo)
    {
        Preconditions.checkNotNull(assignmentRepresentation, "assignment must not be null");

        Installation installation = new Installation(assignmentRepresentation.toAssignment(), binaryRepository, configRepository);

        Predicate<RemoteSlot> slotFilter = SlotFilterBuilder.build(uriInfo);
        List<SlotStatusRepresentation> representations = Lists.newArrayList();
        for (RemoteSlot remoteSlot : console.getAllSlots()) {
            if (slotFilter.apply(remoteSlot)) {
                SlotStatus slotStatus = remoteSlot.assign(installation);
                representations.add(SlotStatusRepresentation.from(slotStatus));

            }
        }
        return Response.ok(representations).build();
    }

    @DELETE
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response clear(@Context UriInfo uriInfo)
    {
        Predicate<RemoteSlot> slotFilter = SlotFilterBuilder.build(uriInfo);
        List<SlotStatusRepresentation> representations = Lists.newArrayList();
        for (RemoteSlot remoteSlot : console.getAllSlots()) {
            if (slotFilter.apply(remoteSlot)) {
                SlotStatus slotStatus = remoteSlot.clear();
                representations.add(SlotStatusRepresentation.from(slotStatus));
            }
        }
        return Response.ok(representations).build();
    }
}
