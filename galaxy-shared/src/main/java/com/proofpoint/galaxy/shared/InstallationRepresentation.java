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
package com.proofpoint.galaxy.shared;

import org.codehaus.jackson.annotate.JsonAutoDetect;
import org.codehaus.jackson.annotate.JsonCreator;
import org.codehaus.jackson.annotate.JsonMethod;
import org.codehaus.jackson.annotate.JsonProperty;

import javax.validation.constraints.NotNull;
import java.net.URI;
import java.util.Map;

@JsonAutoDetect(JsonMethod.NONE)
public class InstallationRepresentation
{
    private final AssignmentRepresentation assignment;
    private final String binaryFile;
    private final String configFile;
    private final Map<String, Integer> resources;

    public static InstallationRepresentation from(Installation installation)
    {
        Assignment assignment = installation.getAssignment();
        return new InstallationRepresentation(
                AssignmentRepresentation.from(assignment),
                installation.getBinaryFile().toString(),
                installation.getConfigFile().toString(),
                installation.getResources());
    }

    @JsonCreator
    public InstallationRepresentation(@JsonProperty("assignment") AssignmentRepresentation assignmentRepresentation,
            @JsonProperty("binaryFile") String binaryFile,
            @JsonProperty("configFile") String configFile,
            @JsonProperty("resources") Map<String, Integer> resources)
    {
        this.assignment = assignmentRepresentation;
        this.binaryFile = binaryFile;
        this.configFile = configFile;
        this.resources = resources;
    }

    @JsonProperty
    @NotNull(message = "is missing")
    public AssignmentRepresentation getAssignment()
    {
        return assignment;
    }

    @JsonProperty
    @NotNull(message = "is missing")
    public String getBinaryFile()
    {
        return binaryFile;
    }

    @JsonProperty
    @NotNull(message = "is missing")
    public String getConfigFile()
    {
        return configFile;
    }

    @JsonProperty
    public Map<String, Integer> getResources()
    {
        return resources;
    }

    public Installation toInstallation()
    {
        Installation installation = new Installation(assignment.toAssignment(), URI.create(binaryFile), URI.create(configFile), resources);
        return installation;
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

        InstallationRepresentation that = (InstallationRepresentation) o;

        if (assignment != null ? !assignment.equals(that.assignment) : that.assignment != null) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode()
    {
        return assignment != null ? assignment.hashCode() : 0;
    }

    @Override
    public String toString()
    {
        final StringBuilder sb = new StringBuilder();
        sb.append("InstallationRepresentation");
        sb.append("{assignment=").append(assignment);
        sb.append(", binaryFile='").append(binaryFile).append('\'');
        sb.append(", configFile=").append(configFile);
        sb.append(", resources=").append(resources);
        sb.append('}');
        return sb.toString();
    }
}
