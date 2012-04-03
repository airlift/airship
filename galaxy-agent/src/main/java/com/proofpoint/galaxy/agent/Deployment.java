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
import com.google.common.collect.ImmutableMap;
import com.proofpoint.galaxy.shared.Assignment;

import javax.annotation.concurrent.Immutable;
import java.io.File;
import java.util.Map;
import java.util.UUID;

@Immutable
public class Deployment
{
    private final UUID nodeId;
    private final String location;
    private final File deploymentDir;
    private final File dataDir;
    private final Assignment assignment;
    private final Map<String, Integer> resources;

    public Deployment(UUID nodeId, String location, File deploymentDir, File dataDir, Assignment assignment, Map<String, Integer> resources)
    {
        Preconditions.checkNotNull(nodeId, "nodeId is null");
        Preconditions.checkNotNull(deploymentDir, "deploymentDir is null");
        Preconditions.checkNotNull(dataDir, "dataDir is null");
        Preconditions.checkNotNull(assignment, "assignment is null");
        Preconditions.checkNotNull(resources, "resources is null");

        this.nodeId = nodeId;
        this.location = location;
        this.deploymentDir = deploymentDir;
        this.dataDir = dataDir;
        this.assignment = assignment;
        this.resources = ImmutableMap.copyOf(resources);
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

    public Map<String, Integer> getResources()
    {
        return resources;
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

        if (assignment != null ? !assignment.equals(that.assignment) : that.assignment != null) {
            return false;
        }
        if (dataDir != null ? !dataDir.equals(that.dataDir) : that.dataDir != null) {
            return false;
        }
        if (deploymentDir != null ? !deploymentDir.equals(that.deploymentDir) : that.deploymentDir != null) {
            return false;
        }
        if (location != null ? !location.equals(that.location) : that.location != null) {
            return false;
        }
        if (nodeId != null ? !nodeId.equals(that.nodeId) : that.nodeId != null) {
            return false;
        }
        if (resources != null ? !resources.equals(that.resources) : that.resources != null) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode()
    {
        int result = (nodeId != null ? nodeId.hashCode() : 0);
        result = 31 * result + (location != null ? location.hashCode() : 0);
        result = 31 * result + (deploymentDir != null ? deploymentDir.hashCode() : 0);
        result = 31 * result + (dataDir != null ? dataDir.hashCode() : 0);
        result = 31 * result + (assignment != null ? assignment.hashCode() : 0);
        result = 31 * result + (resources != null ? resources.hashCode() : 0);
        return result;
    }

    @Override
    public String toString()
    {
        final StringBuilder sb = new StringBuilder();
        sb.append("Deployment");
        sb.append("{nodeId=").append(nodeId);
        sb.append(", location=").append(location);
        sb.append(", deploymentDir=").append(deploymentDir);
        sb.append(", dataDir=").append(dataDir);
        sb.append(", assignment=").append(assignment);
        sb.append(", resources=").append(resources);
        sb.append('}');
        return sb.toString();
    }
}
