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
import com.google.inject.Inject;
import com.proofpoint.galaxy.shared.InstallationRepresentation;
import com.proofpoint.galaxy.shared.SlotStatus;
import com.proofpoint.galaxy.shared.SlotStatusRepresentation;

import javax.ws.rs.Consumes;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import java.util.UUID;

import static com.proofpoint.galaxy.shared.VersionsUtil.checkAgentVersion;
import static com.proofpoint.galaxy.shared.VersionsUtil.checkSlotVersion;
import static com.proofpoint.galaxy.shared.VersionsUtil.GALAXY_AGENT_VERSION_HEADER;
import static com.proofpoint.galaxy.shared.VersionsUtil.GALAXY_SLOT_VERSION_HEADER;

@Path("/v1/agent/slot/{slotId}/assignment")
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
    public Response assign(@HeaderParam(GALAXY_AGENT_VERSION_HEADER) String agentVersion,
            @HeaderParam(GALAXY_SLOT_VERSION_HEADER) String slotVersion,
            @PathParam("slotId") UUID slotId,
            InstallationRepresentation installation)
    {
        Preconditions.checkNotNull(slotId, "slotId must not be null");
        Preconditions.checkNotNull(installation, "installation must not be null");

        Slot slot = agent.getSlot(slotId);
        if (slot == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        checkAgentVersion(agent.getAgentStatus(), agentVersion);
        checkSlotVersion(slot.status(), slotVersion);

        SlotStatus status = slot.assign(installation.toInstallation());
        return Response.ok(SlotStatusRepresentation.from(status))
                .header(GALAXY_AGENT_VERSION_HEADER, agent.getAgentStatus().getVersion())
                .header(GALAXY_SLOT_VERSION_HEADER, status.getVersion())
                .build();
    }
}
