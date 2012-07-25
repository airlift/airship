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
import com.proofpoint.discovery.client.ServiceDescriptorsRepresentation;
import com.proofpoint.node.NodeInfo;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

@Path("/v1/serviceInventory")
public class ServiceInventoryResource
{
    private final Coordinator coordinator;
    private final ServiceInventory serviceInventory;
    private final String environment;

    @Inject
    public ServiceInventoryResource(Coordinator coordinator, ServiceInventory serviceInventory, NodeInfo nodeInfo)
    {
        Preconditions.checkNotNull(coordinator, "coordinator is null");
        Preconditions.checkNotNull(serviceInventory, "serviceInventory is null");
        Preconditions.checkNotNull(nodeInfo, "nodeInfo is null");

        this.coordinator = coordinator;
        this.serviceInventory = serviceInventory;
        this.environment = nodeInfo.getEnvironment();
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response getServiceInventory()
    {
        return Response.ok(new ServiceDescriptorsRepresentation(environment, serviceInventory.getServiceInventory(coordinator.getAllSlotStatus()))).build();
    }
}
