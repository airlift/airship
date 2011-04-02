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
import com.proofpoint.galaxy.shared.Installation;

import java.io.File;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

public class MockDeploymentManager implements DeploymentManager
{
    private final AtomicInteger nextId = new AtomicInteger(1);
    private final UUID slotId = UUID.randomUUID();
    private final Map<String, Deployment> deployments = new TreeMap<String, Deployment>();
    private Deployment activeDeployment;

    @Override
    public Deployment install(Installation installation)
    {
        Preconditions.checkNotNull(installation, "installation is null");

        String deploymentId = "Deployment-" + nextId.getAndIncrement();
        Deployment deployment = new Deployment(deploymentId, new File(deploymentId), installation.getAssignment());
        deployments.put(deploymentId, deployment);
        return deployment;
    }

    public UUID getSlotId()
    {
        return slotId;
    }

    @Override
    public Deployment getActiveDeployment()
    {
        return activeDeployment;
    }

    @Override
    public Deployment activate(String deploymentId)
    {
        Preconditions.checkNotNull(deploymentId, "deploymentId is null");
        Deployment deployment = deployments.get(deploymentId);
        if (deployment == null) {
            throw new IllegalArgumentException("Unknown deployment id");
        }

        activeDeployment = deployment;
        return deployment;
    }

    @Override
    public void remove(String deploymentId)
    {
        Preconditions.checkNotNull(deploymentId, "deploymentId is null");
        if (activeDeployment != null && deploymentId.equals(activeDeployment.getDeploymentId())) {
            activeDeployment = null;
        }
        deployments.remove(deploymentId);
    }
}
