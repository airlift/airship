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

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMap.Builder;
import com.proofpoint.galaxy.Assignment;
import com.proofpoint.galaxy.console.AssignmentRepresentation;
import org.codehaus.jackson.annotate.JsonAutoDetect;
import org.codehaus.jackson.annotate.JsonCreator;
import org.codehaus.jackson.annotate.JsonMethod;
import org.codehaus.jackson.annotate.JsonProperty;

import javax.validation.constraints.NotNull;
import java.net.URI;
import java.util.Map;
import java.util.Map.Entry;

@JsonAutoDetect(JsonMethod.NONE)
public class InstallationRepresentation
{
    private final AssignmentRepresentation assignment;
    private final String binaryFile;
    private final Map<String,String> configFiles;

    public static InstallationRepresentation from(Installation installation)
    {
        Builder<String, String> builder = ImmutableMap.builder();
        for (Entry<String, URI> entry : installation.getConfigFiles().entrySet()) {
            builder.put(entry.getKey(), entry.getValue().toString());
        }

        Assignment assignment = installation.getAssignment();
        return new InstallationRepresentation(
                AssignmentRepresentation.from(assignment),
                installation.getBinaryFile().toString(),
                builder.build());
    }

    @JsonCreator
    public InstallationRepresentation(@JsonProperty("assignment") AssignmentRepresentation assignmentRepresentation,
            @JsonProperty("binaryFile") String binaryFile,
            @JsonProperty("configFiles") Map<String, String> configFiles)
    {
        this.assignment = assignmentRepresentation;
        this.binaryFile = binaryFile;
        this.configFiles = configFiles;
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
    public Map<String, String> getConfigFiles()
    {
        return configFiles;
    }

    public Installation toInstallation()
    {
        Builder<String, URI> builder = ImmutableMap.builder();
        for (Entry<String, String> entry : configFiles.entrySet()) {
            builder.put(entry.getKey(), URI.create(entry.getValue()));
        }
        Installation installation = new Installation(assignment.toAssignment(), URI.create(binaryFile), builder.build());
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
        final StringBuffer sb = new StringBuffer();
        sb.append("InstallationRepresentation");
        sb.append("{assignment=").append(assignment);
        sb.append(", binaryFile='").append(binaryFile).append('\'');
        sb.append(", configFiles=").append(configFiles);
        sb.append('}');
        return sb.toString();
    }
}
