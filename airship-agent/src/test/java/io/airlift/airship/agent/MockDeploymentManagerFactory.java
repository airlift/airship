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
package io.airlift.airship.agent;

import com.google.common.collect.ImmutableList;
import io.airlift.airship.shared.Installation;

import java.util.List;

public class MockDeploymentManagerFactory
        implements DeploymentManagerFactory
{
    @Override
    public List<DeploymentManager> loadSlots()
    {
        return ImmutableList.of();
    }

    @Override
    public DeploymentManager createDeploymentManager(Installation installation)
    {
        return new MockDeploymentManager();
    }
}
