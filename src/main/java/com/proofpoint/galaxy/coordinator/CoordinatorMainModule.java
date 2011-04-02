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
package com.proofpoint.galaxy.coordinator;

import com.google.inject.Binder;
import com.google.inject.Module;
import com.google.inject.Scopes;
import com.proofpoint.configuration.ConfigurationModule;

public class CoordinatorMainModule
        implements Module
{
    public void configure(Binder binder)
    {
        binder.bind(Coordinator.class).in(Scopes.SINGLETON);
        binder.bind(CoordinatorSlotResource.class).in(Scopes.SINGLETON);
        binder.bind(CoordinatorAssignmentResource.class).in(Scopes.SINGLETON);
        binder.bind(CoordinatorLifecycleResource.class).in(Scopes.SINGLETON);
        binder.bind(AnnounceResource.class).in(Scopes.SINGLETON);
        binder.bind(RemoteSlotFactory.class).to(HttpRemoteSlotFactory.class).in(Scopes.SINGLETON);
        binder.bind(BinaryRepository.class).to(MavenBinaryRepository.class).in(Scopes.SINGLETON);
        binder.bind(ConfigRepository.class).to(SimpleConfigRepository.class).in(Scopes.SINGLETON);
        ConfigurationModule.bindConfig(binder).to(CoordinatorConfig.class);
    }
}
