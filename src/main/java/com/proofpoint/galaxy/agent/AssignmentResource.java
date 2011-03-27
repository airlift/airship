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
package com.proofpoint.galaxy.agent;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.inject.Inject;
import com.proofpoint.galaxy.Slot;
import com.proofpoint.galaxy.SlotStatus;
import com.proofpoint.galaxy.SlotStatusRepresentation;

import javax.validation.ConstraintViolation;
import javax.validation.Validation;
import javax.validation.Validator;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;
import java.util.Collection;
import java.util.List;
import java.util.Set;

@Path("/v1/slot/{slotName: [a-z0-9]+}/assignment")
public class AssignmentResource
{
    private final Agent agent;

    @Inject
    public AssignmentResource(Agent agent)
    {
        Preconditions.checkNotNull(agent, "slotsManager must not be null");

        this.agent = agent;
    }

    @PUT
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response assign(@PathParam("slotName") String slotName, InstallationRepresentation installation, @Context UriInfo uriInfo)
    {
        Preconditions.checkNotNull(slotName, "slotName must not be null");
        Preconditions.checkNotNull(installation, "installation must not be null");

        Slot slot = agent.getSlot(slotName);
        if (slot == null) {
            return Response.status(Response.Status.NOT_FOUND).entity("[" + slotName + "]").build();
        }

        Set<ConstraintViolation<InstallationRepresentation>> violations = validate(installation);
        if (!violations.isEmpty()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(messagesFor(violations))
                    .build();
        }

        SlotStatus status = slot.assign(installation.toInstallation());
        return Response.ok(SlotStatusRepresentation.from(status)).build();
    }

    @DELETE
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response clear(@PathParam("slotName") String slotName, @Context UriInfo uriInfo)
    {
        Preconditions.checkNotNull(slotName, "slotName must not be null");

        Slot slot = agent.getSlot(slotName);
        if (slot == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        SlotStatus status = slot.clear();
        return Response.ok(SlotStatusRepresentation.from(status)).build();
    }

    private static <T> Set<ConstraintViolation<T>> validate(T object)
    {
        Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
        return validator.validate(object);
    }

    private static List<String> messagesFor(Collection<? extends ConstraintViolation<?>> violations)
    {
        ImmutableList.Builder<String> messages = new ImmutableList.Builder<String>();
        for (ConstraintViolation<?> violation : violations) {
            messages.add(violation.getPropertyPath().toString() + " " + violation.getMessage());
        }

        return messages.build();
    }

}
