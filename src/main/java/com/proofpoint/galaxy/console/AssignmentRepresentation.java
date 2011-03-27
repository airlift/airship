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
package com.proofpoint.galaxy.console;

import com.proofpoint.galaxy.Assignment;
import com.proofpoint.galaxy.BinarySpec;
import com.proofpoint.galaxy.ConfigSpec;
import org.codehaus.jackson.annotate.JsonAutoDetect;
import org.codehaus.jackson.annotate.JsonCreator;
import org.codehaus.jackson.annotate.JsonMethod;
import org.codehaus.jackson.annotate.JsonProperty;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;

@JsonAutoDetect(JsonMethod.NONE)
public class AssignmentRepresentation
{
    private final String binary;
    private final String config;

    public static AssignmentRepresentation from(Assignment assignment)
    {
        return new AssignmentRepresentation(assignment.getBinary().toString(), assignment.getConfig().toString());
    }

    @JsonCreator
    public AssignmentRepresentation(@JsonProperty("binary") String binary, @JsonProperty("config") String config)
    {
        this.binary = binary;
        this.config = config;
    }

    @JsonProperty
    @NotNull(message = "is missing")
    @Pattern(regexp = BinarySpec.BINARY_SPEC_REGEX, message = "is malformed")
    public String getBinary()
    {
        return binary;
    }

    @JsonProperty
    @NotNull(message = "is missing")
    @Pattern(regexp = ConfigSpec.CONFIG_SPEC_REGEX, message = "is malformed")
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
        final StringBuffer sb = new StringBuffer();
        sb.append("AssignmentRepresentation");
        sb.append("{binary='").append(binary).append('\'');
        sb.append(", config='").append(config).append('\'');
        sb.append('}');
        return sb.toString();
    }
}
