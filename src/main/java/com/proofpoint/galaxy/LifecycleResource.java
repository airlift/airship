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
package com.proofpoint.galaxy;

import com.google.common.base.Preconditions;
import com.google.inject.Inject;

import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;

@Path("/v1/slot/{slotName: [a-z0-9]+}/lifecycle")
public class LifecycleResource
{
    private final Agent agent;

    @Inject
    public LifecycleResource(Agent agent)
    {
        Preconditions.checkNotNull(agent, "slotsManager must not be null");

        this.agent = agent;
    }

    @PUT
    @Produces(MediaType.APPLICATION_JSON)
    public Response setState(@PathParam("slotName") String slotName, String newState, @Context UriInfo uriInfo)
    {
        Preconditions.checkNotNull(slotName, "slotName must not be null");
        Preconditions.checkNotNull(newState, "newState must not be null");

        SlotManager slotManager = agent.getSlot(slotName);
        if (slotManager == null) {
            return Response.status(Response.Status.NOT_FOUND).entity("[" + slotName + "]").build();
        }

        SlotStatus status;
        if ("start".equals(newState)) {
            status = slotManager.start();
        }
        else if ("restart".equals(newState)) {
            status = slotManager.restart();
        }
        else if ("stop".equals(newState)) {
            status = slotManager.stop();
        }
        else {
            return Response.status(Response.Status.BAD_REQUEST).build();
        }
        return Response.ok(SlotStatusRepresentation.from(status, uriInfo.getBaseUri())).build();
    }
}
