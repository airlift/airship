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
import com.google.common.base.Throwables;
import com.google.common.collect.Lists;
import com.google.inject.Inject;
import com.proofpoint.galaxy.shared.InstallationRepresentation;
import com.proofpoint.galaxy.shared.SlotStatus;
import com.proofpoint.galaxy.shared.SlotStatusRepresentation;

import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;
import java.net.URI;
import java.util.List;

@Path("/v1/agent/slot")
public class SlotResource
{
    private final Agent agent;
    private final AnnouncementService announcementService;

    @Inject
    public SlotResource(Agent agent, AnnouncementService announcementService)
    {
        Preconditions.checkNotNull(agent, "agent is null");
        Preconditions.checkNotNull(announcementService, "announcementService is null");

        this.agent = agent;
        this.announcementService = announcementService;
    }

    @POST
    public Response addSlot(@Context UriInfo uriInfo)
    {
        Slot slot = agent.addNewSlot();
        try {
            announcementService.announce();
        }
        catch (Exception ignored) {
        }
        return Response.created(getSelfUri(slot.getName(), uriInfo.getBaseUri())).build();
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response installSlot(InstallationRepresentation installation, @Context UriInfo uriInfo)
    {
        Preconditions.checkNotNull(installation, "installation must not be null");

        // create a new slot
        Slot slot = agent.addNewSlot();

        // install the software
        try {
            SlotStatus status = slot.assign(installation.toInstallation());
            return Response
                    .created(getSelfUri(slot.getName(), uriInfo.getBaseUri()))
                    .entity(SlotStatusRepresentation.from(status))
                    .build();
        }
        catch (Exception e) {
            agent.terminateSlot(slot.getName());
            throw Throwables.propagate(e);
        }
    }

    @Path("{slotName: [a-z0-9]+}")
    @DELETE
    public Response terminateSlot(@PathParam("slotName") String id)
    {
        Preconditions.checkNotNull(id, "id must not be null");

        SlotStatus slotStatus = agent.terminateSlot(id);
        if (slotStatus == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        return Response.ok(SlotStatusRepresentation.from(slotStatus)).build();
    }

    @Path("{slotName: [a-z0-9]+}")
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response getSlotStatus(@PathParam("slotName") String slotName, @Context UriInfo uriInfo)
    {
        Preconditions.checkNotNull(slotName, "slotName must not be null");

        Slot slot = agent.getSlot(slotName);
        if (slot == null) {
            return Response.status(Response.Status.NOT_FOUND).entity("[" + slotName + "]").build();
        }

        SlotStatus slotStatus = slot.status();
        return Response.ok(SlotStatusRepresentation.from(slotStatus)).build();
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response getAllSlotsStatus(@Context UriInfo uriInfo)
    {
        List<SlotStatusRepresentation> representations = Lists.newArrayList();
        for (Slot slot : agent.getAllSlots()) {
            SlotStatus slotStatus = slot.status();
            representations.add(SlotStatusRepresentation.from(slotStatus));
        }
        return Response.ok(representations).build();
    }


    private static URI getSelfUri(String slotName, URI baseUri)
    {
        return baseUri.resolve("/v1/agent/slot/" + slotName);
    }
}
