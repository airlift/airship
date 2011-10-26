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
import com.google.common.collect.Lists;
import com.google.inject.Inject;
import com.proofpoint.galaxy.shared.InstallationRepresentation;
import com.proofpoint.galaxy.shared.SlotStatus;
import com.proofpoint.galaxy.shared.SlotStatusRepresentation;

import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.HeaderParam;
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

import static com.proofpoint.galaxy.agent.VersionsUtil.checkAgentVersion;
import static com.proofpoint.galaxy.agent.VersionsUtil.checkSlotVersion;
import static com.proofpoint.galaxy.shared.AgentStatusRepresentation.GALAXY_AGENT_VERSION_HEADER;
import static com.proofpoint.galaxy.shared.SlotStatusRepresentation.GALAXY_SLOT_VERSION_HEADER;

@Path("/v1/agent/slot")
public class SlotResource
{
    private final Agent agent;

    @Inject
    public SlotResource(Agent agent)
    {
        Preconditions.checkNotNull(agent, "agent is null");

        this.agent = agent;
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response installSlot(@HeaderParam(GALAXY_AGENT_VERSION_HEADER) String agentVersion, InstallationRepresentation installation, @Context UriInfo uriInfo)
    {
        Preconditions.checkNotNull(installation, "installation must not be null");

        checkAgentVersion(agent, agentVersion);

        SlotStatus slotStatus = agent.install(installation.toInstallation());

        return Response
                .created(getSelfUri(slotStatus.getName(), uriInfo.getBaseUri()))
                .entity(SlotStatusRepresentation.from(slotStatus))
                .header(GALAXY_AGENT_VERSION_HEADER, agent.getAgentStatus().getVersion())
                .header(GALAXY_SLOT_VERSION_HEADER, slotStatus.getVersion())
                .build();
    }

    @Path("{slotName: [a-z0-9_.-]+}")
    @DELETE
    public Response terminateSlot(@HeaderParam(GALAXY_AGENT_VERSION_HEADER) String agentVersion,
            @HeaderParam(GALAXY_SLOT_VERSION_HEADER) String slotVersion,
            @PathParam("slotName") String id)
    {
        Preconditions.checkNotNull(id, "id must not be null");

        checkSlotVersion(id, slotVersion, agent, agentVersion);

        SlotStatus slotStatus = agent.terminateSlot(id);
        if (slotStatus == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        return Response.ok(SlotStatusRepresentation.from(slotStatus))
                .header(GALAXY_AGENT_VERSION_HEADER, agent.getAgentStatus().getVersion())
                .header(GALAXY_SLOT_VERSION_HEADER, slotStatus.getVersion())
                .build();
    }

    @Path("{slotName: [a-z0-9_.-]+}")
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
        return Response.ok(SlotStatusRepresentation.from(slotStatus))
                .header(GALAXY_AGENT_VERSION_HEADER, agent.getAgentStatus().getVersion())
                .header(GALAXY_SLOT_VERSION_HEADER, slotStatus.getVersion())
                .build();
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
        return Response.ok(representations)
                .header(GALAXY_AGENT_VERSION_HEADER, agent.getAgentStatus().getVersion())
                .build();
    }


    private static URI getSelfUri(String slotName, URI baseUri)
    {
        return baseUri.resolve("/v1/agent/slot/" + slotName);
    }
}
