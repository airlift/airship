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

import com.google.inject.Binder;
import com.google.inject.Module;
import com.google.inject.Scopes;
import com.google.inject.multibindings.Multibinder;
import io.airlift.airship.coordinator.auth.AuthConfig;
import io.airlift.airship.coordinator.auth.AuthFilter;
import io.airlift.airship.coordinator.auth.SignatureVerifier;
import io.airlift.airship.shared.AgentStatusRepresentation;
import io.airlift.airship.shared.CoordinatorStatusRepresentation;
import io.airlift.airship.shared.ExpectedSlotStatus;
import io.airlift.airship.shared.InstallationRepresentation;
import io.airlift.airship.shared.Repository;
import io.airlift.airship.shared.RepositorySet;
import io.airlift.airship.shared.SlotStatusRepresentation;
import io.airlift.airship.shared.VersionConflictExceptionMapper;
import io.airlift.discovery.client.ServiceDescriptor;
import io.airlift.discovery.client.ServiceDescriptorsRepresentation;
import io.airlift.http.server.TheServlet;
import io.airlift.json.JsonCodecBinder;

import javax.servlet.Filter;

import static io.airlift.configuration.ConfigurationModule.bindConfig;
import static io.airlift.http.client.HttpClientBinder.httpClientBinder;

public class CoordinatorMainModule
        implements Module
{
    public void configure(Binder binder)
    {
        binder.disableCircularProxies();
        binder.requireExplicitBindings();

        binder.bind(Coordinator.class).in(Scopes.SINGLETON);
        binder.bind(CoordinatorResource.class).in(Scopes.SINGLETON);
        binder.bind(CoordinatorSlotResource.class).in(Scopes.SINGLETON);
        binder.bind(CoordinatorAssignmentResource.class).in(Scopes.SINGLETON);
        binder.bind(CoordinatorLifecycleResource.class).in(Scopes.SINGLETON);
        binder.bind(ExpectedStateResource.class).in(Scopes.SINGLETON);
        binder.bind(InvalidSlotFilterExceptionMapper.class).in(Scopes.SINGLETON);
        binder.bind(AdminResource.class).in(Scopes.SINGLETON);
        binder.bind(VersionConflictExceptionMapper.class).in(Scopes.SINGLETON);
        binder.bind(RemoteCoordinatorFactory.class).to(HttpRemoteCoordinatorFactory.class).in(Scopes.SINGLETON);
        binder.bind(RemoteAgentFactory.class).to(HttpRemoteAgentFactory.class).in(Scopes.SINGLETON);

        binder.bind(Repository.class).to(RepositorySet.class).in(Scopes.SINGLETON);
        Multibinder.newSetBinder(binder, Repository.class).addBinding().to(MavenRepository.class).in(Scopes.SINGLETON);
        Multibinder.newSetBinder(binder, Repository.class).addBinding().to(HttpRepository.class).in(Scopes.SINGLETON);

        binder.bind(BinaryResource.class).in(Scopes.SINGLETON);

        binder.bind(ServiceInventory.class).to(HttpServiceInventory.class).in(Scopes.SINGLETON);
        binder.bind(ServiceInventoryResource.class).in(Scopes.SINGLETON);

        binder.bind(SignatureVerifier.class).in(Scopes.SINGLETON);
        Multibinder.newSetBinder(binder, Filter.class, TheServlet.class).addBinding().to(AuthFilter.class).in(Scopes.SINGLETON);
        bindConfig(binder).to(AuthConfig.class);

        JsonCodecBinder.jsonCodecBinder(binder).bindJsonCodec(InstallationRepresentation.class);
        JsonCodecBinder.jsonCodecBinder(binder).bindJsonCodec(CoordinatorStatusRepresentation.class);
        JsonCodecBinder.jsonCodecBinder(binder).bindJsonCodec(AgentStatusRepresentation.class);
        JsonCodecBinder.jsonCodecBinder(binder).bindJsonCodec(SlotStatusRepresentation.class);
        JsonCodecBinder.jsonCodecBinder(binder).bindJsonCodec(ServiceDescriptorsRepresentation.class);
        JsonCodecBinder.jsonCodecBinder(binder).bindJsonCodec(ExpectedSlotStatus.class);
        JsonCodecBinder.jsonCodecBinder(binder).bindListJsonCodec(ServiceDescriptor.class);

        bindConfig(binder).to(CoordinatorConfig.class);

        httpClientBinder(binder).bindHttpClient("global", Global.class);
    }
}
