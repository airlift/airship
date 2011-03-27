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
import com.proofpoint.galaxy.Assignment;

import java.io.File;

public class Deployment
{
    private final String deploymentId;
    private final File deploymentDir;
    private final Assignment assignment;

    public Deployment(String deploymentId, File deploymentDir, Assignment assignment)
    {
        Preconditions.checkNotNull(deploymentId, "deploymentId is null");
        Preconditions.checkNotNull(deploymentDir, "deploymentDir is null");
        Preconditions.checkNotNull(assignment, "assignment is null");

        this.deploymentId = deploymentId;
        this.deploymentDir = deploymentDir;
        this.assignment = assignment;
    }

    public String getDeploymentId()
    {
        return deploymentId;
    }

    public File getDeploymentDir()
    {
        return deploymentDir;
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
        final StringBuffer sb = new StringBuffer();
        sb.append("Deployment");
        sb.append("{deploymentId='").append(deploymentId).append('\'');
        sb.append(", deploymentDir=").append(deploymentDir);
        sb.append(", assignment=").append(assignment);
        sb.append('}');
        return sb.toString();
    }
}
