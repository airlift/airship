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
import com.google.inject.Inject;
import io.airlift.airship.shared.CoordinatorStatus;
import io.airlift.airship.shared.CoordinatorStatusRepresentation;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import static io.airlift.airship.shared.VersionsUtil.AIRSHIP_COORDINATOR_VERSION_HEADER;

@Path("/v1/coordinator/")
public class CoordinatorResource
{
    private final Coordinator coordinator;

    @Inject
    public CoordinatorResource(Coordinator coordinator)
    {
        Preconditions.checkNotNull(coordinator, "coordinator is null");

        this.coordinator = coordinator;
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response getAllSlotsStatus()
    {
        CoordinatorStatus coordinatorStatus = coordinator.status();
        CoordinatorStatusRepresentation coordinatorStatusRepresentation = CoordinatorStatusRepresentation.from(coordinatorStatus);
        return Response.ok(coordinatorStatusRepresentation)
                .header(AIRSHIP_COORDINATOR_VERSION_HEADER, coordinatorStatus.getVersion())
                .build();
    }
}
