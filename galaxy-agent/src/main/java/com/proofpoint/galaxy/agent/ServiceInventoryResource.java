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

import com.proofpoint.discovery.client.ServiceDescriptorsRepresentation;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import java.util.concurrent.atomic.AtomicReference;

@Path("/v1/serviceInventory")
public class ServiceInventoryResource
{
    private final AtomicReference<ServiceDescriptorsRepresentation> descriptor = new AtomicReference<ServiceDescriptorsRepresentation>();

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response getServiceInventory()
    {
        ServiceDescriptorsRepresentation descriptor = this.descriptor.get();
        if (descriptor == null) {
            return Response.status(Status.NO_CONTENT).build();
        }
        return Response.ok(descriptor).build();
    }

    @PUT
    @Consumes(MediaType.APPLICATION_JSON)
    public Response setServiceInventory(ServiceDescriptorsRepresentation descriptor)
    {
        this.descriptor.set(descriptor);
        return Response.ok().build();
    }

}
