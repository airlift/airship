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

import com.google.inject.Binder;
import com.google.inject.Module;
import com.google.inject.Scopes;
import com.proofpoint.configuration.ConfigurationModule;
import com.proofpoint.galaxy.shared.VersionConflictExceptionMapper;
import org.weakref.jmx.guice.MBeanModule;

public class AgentMainModule
        implements Module
{
    public void configure(Binder binder)
    {
        binder.disableCircularProxies();
        binder.requireExplicitBindings();

        binder.bind(Agent.class).in(Scopes.SINGLETON);
        MBeanModule.newExporter(binder).export(Agent.class).withGeneratedName();

        binder.bind(AgentResource.class).in(Scopes.SINGLETON);

        binder.bind(SlotResource.class).in(Scopes.SINGLETON);
        binder.bind(AssignmentResource.class).in(Scopes.SINGLETON);
        binder.bind(LifecycleResource.class).in(Scopes.SINGLETON);
        binder.bind(VersionConflictExceptionMapper.class).in(Scopes.SINGLETON);

        binder.bind(DeploymentManagerFactory.class).to(DirectoryDeploymentManagerFactory.class).in(Scopes.SINGLETON);
        binder.bind(LifecycleManager.class).to(LauncherLifecycleManager.class).in(Scopes.SINGLETON);

        binder.bind(ServiceInventoryResource.class).in(Scopes.SINGLETON);

        ConfigurationModule.bindConfig(binder).to(AgentConfig.class);
    }
}
