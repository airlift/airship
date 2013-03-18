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

import com.google.common.base.Preconditions;
import io.airlift.airship.shared.Installation;

import java.io.File;
import java.util.UUID;

public class MockDeploymentManager implements DeploymentManager
{
    private final String location;
    private final UUID slotId = UUID.randomUUID();
    private Deployment deployment;

    public MockDeploymentManager()
    {
        this.location = "/location/" + slotId;
    }

    @Override
    public Deployment install(Installation installation)
    {
        Preconditions.checkNotNull(installation, "installation is null");
        Preconditions.checkState(deployment == null, "slot has an active deployment");

        deployment = new Deployment(UUID.randomUUID(), location, new File("installation"), new File("data"), installation.getAssignment(), installation.getResources());
        return deployment;
    }

    public UUID getSlotId()
    {
        return slotId;
    }

    @Override
    public String getLocation()
    {
        return location;
    }

    @Override
    public Deployment getDeployment()
    {
        return deployment;
    }

    @Override
    public void clear()
    {
        deployment = null;
    }

    @Override
    public void terminate()
    {
        deployment = null;
    }

    @Override
    public File hackGetDataDir()
    {
        return new File("data");
    }
}
