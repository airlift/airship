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
import com.proofpoint.galaxy.shared.AgentStatus;
import com.proofpoint.galaxy.shared.AgentStatusRepresentation;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;

@Path("/v1/agent/")
public class AgentResource
{
    private final Agent agent;

    @Inject
    public AgentResource(Agent agent)
    {
        Preconditions.checkNotNull(agent, "agent is null");

        this.agent = agent;
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response getAllSlotsStatus(@Context UriInfo uriInfo)
    {
        AgentStatus agentStatus = agent.getAgentStatus();
        AgentStatusRepresentation agentStatusRepresentation = AgentStatusRepresentation.from(agentStatus);
        return Response.ok(agentStatusRepresentation).build();
    }
}
