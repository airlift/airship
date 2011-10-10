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
import com.proofpoint.galaxy.shared.Assignment;

import javax.annotation.concurrent.Immutable;
import java.io.File;
import java.util.UUID;

@Immutable
public class Deployment
{
    private final String slotName;
    private final String deploymentId;
    private final UUID nodeId;
    private final String location;
    private final File deploymentDir;
    private final File dataDir;
    private final Assignment assignment;

    public Deployment(String deploymentId, String slotName, UUID nodeId, String location, File deploymentDir, File dataDir, Assignment assignment)
    {
        Preconditions.checkNotNull(deploymentId, "deploymentId is null");
        Preconditions.checkNotNull(slotName, "slotName is null");
        Preconditions.checkNotNull(nodeId, "nodeId is null");
        Preconditions.checkNotNull(deploymentDir, "deploymentDir is null");
        Preconditions.checkNotNull(dataDir, "dataDir is null");
        Preconditions.checkNotNull(assignment, "assignment is null");

        this.slotName = slotName;
        this.deploymentId = deploymentId;
        this.nodeId = nodeId;
        this.location = location;
        this.deploymentDir = deploymentDir;
        this.dataDir = dataDir;
        this.assignment = assignment;
    }

    public String getDeploymentId()
    {
        return deploymentId;
    }

    public String getSlotName()
    {
        return slotName;
    }

    public UUID getNodeId()
    {
        return nodeId;
    }

    public String getLocation()
    {
        return location;
    }

    public File getDeploymentDir()
    {
        return deploymentDir;
    }

    public File getDataDir()
    {
        return dataDir;
    }

    public Assignment getAssignment()
    {
        return assignment;
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        Deployment that = (Deployment) o;

        if (!deploymentId.equals(that.deploymentId)) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode()
    {
        return deploymentId.hashCode();
    }

    @Override
    public String toString()
    {
        final StringBuilder sb = new StringBuilder();
        sb.append("Deployment");
        sb.append("{deploymentId='").append(deploymentId).append('\'');
        sb.append(", slotName='").append(slotName).append('\'');
        sb.append(", nodeId=").append(nodeId);
        sb.append(", location=").append(location);
        sb.append(", deploymentDir=").append(deploymentDir);
        sb.append(", dataDir=").append(dataDir);
        sb.append(", assignment=").append(assignment);
        sb.append('}');
        return sb.toString();
    }
}
