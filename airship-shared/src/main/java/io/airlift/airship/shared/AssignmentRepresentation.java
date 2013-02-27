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
package io.airlift.airship.shared;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import javax.validation.constraints.NotNull;

public class AssignmentRepresentation
{
    private final String binary;
    private final String config;

    public static AssignmentRepresentation from(Assignment assignment)
    {
        return new AssignmentRepresentation(assignment.getBinary(), assignment.getConfig());
    }

    @JsonCreator
    public AssignmentRepresentation(@JsonProperty("binary") String binary, @JsonProperty("config") String config)
    {
        this.binary = binary;
        this.config = config;
    }

    @JsonProperty
    @NotNull(message = "is missing")
    public String getBinary()
    {
        return binary;
    }

    @JsonProperty
    @NotNull(message = "is missing")
    public String getConfig()
    {
        return config;
    }

    public Assignment toAssignment()
    {
        Assignment assignment = new Assignment(binary, config);
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

        AssignmentRepresentation that = (AssignmentRepresentation) o;

        if (binary != null ? !binary.equals(that.binary) : that.binary != null) {
            return false;
        }
        if (config != null ? !config.equals(that.config) : that.config != null) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode()
    {
        int result = binary != null ? binary.hashCode() : 0;
        result = 31 * result + (config != null ? config.hashCode() : 0);
        return result;
    }

    @Override
    public String toString()
    {
        final StringBuilder sb = new StringBuilder();
        sb.append("AssignmentRepresentation");
        sb.append("{binary='").append(binary).append('\'');
        sb.append(", config='").append(config).append('\'');
        sb.append('}');
        return sb.toString();
    }
}
