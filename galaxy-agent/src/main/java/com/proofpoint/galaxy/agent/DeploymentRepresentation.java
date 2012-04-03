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
import com.proofpoint.galaxy.shared.AssignmentRepresentation;
import org.codehaus.jackson.annotate.JsonCreator;
import org.codehaus.jackson.annotate.JsonProperty;

import java.io.File;
import java.util.Map;
import java.util.UUID;

public class DeploymentRepresentation
{
    private final UUID nodeId;
    private final AssignmentRepresentation assignment;
    private final Map<String, Integer> resources;

    public static DeploymentRepresentation from(Deployment deployment)
    {
        return new DeploymentRepresentation(
                deployment.getNodeId(),
                AssignmentRepresentation.from(deployment.getAssignment()),
                deployment.getResources());
    }

    @JsonCreator
    public DeploymentRepresentation(
            @JsonProperty("nodeId") UUID nodeId,
            @JsonProperty("assignment") AssignmentRepresentation assignment,
            @JsonProperty("resources") Map<String, Integer> resources)
    {
        Preconditions.checkNotNull(nodeId, "nodeId is null");
        Preconditions.checkNotNull(assignment, "assignment is null");

        this.nodeId = nodeId;
        this.assignment = assignment;
        if (resources == null) {
            this.resources = ImmutableMap.of();
        } else {
            this.resources = ImmutableMap.copyOf(resources);
        }
    }

    @JsonProperty
    public UUID getNodeId()
    {
        return nodeId;
    }

    @JsonProperty
    public AssignmentRepresentation getAssignment()
    {
        return assignment;
    }

    @JsonProperty
    public Map<String, Integer> getResources()
    {
        return resources;
    }

    public Deployment toDeployment(File deploymentDir, File dataDir, String location)
    {
        return new Deployment(nodeId, location, deploymentDir, dataDir, assignment.toAssignment(), resources);
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

        DeploymentRepresentation that = (DeploymentRepresentation) o;

        if (assignment != null ? !assignment.equals(that.assignment) : that.assignment != null) {
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
        result = 31 * result + (assignment != null ? assignment.hashCode() : 0);
        result = 31 * result + (resources != null ? resources.hashCode() : 0);
        return result;
    }

    @Override
    public String toString()
    {
        final StringBuilder sb = new StringBuilder();
        sb.append("DeploymentRepresentation");
        sb.append("{nodeId=").append(nodeId);
        sb.append(", assignment=").append(assignment);
        sb.append(", resources=").append(resources);
        sb.append('}');
        return sb.toString();
    }
}
