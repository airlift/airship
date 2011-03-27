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
package com.proofpoint.galaxy;

import org.codehaus.jackson.annotate.JsonAutoDetect;
import org.codehaus.jackson.annotate.JsonCreator;
import org.codehaus.jackson.annotate.JsonMethod;
import org.codehaus.jackson.annotate.JsonProperty;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;
import java.net.URI;
import java.util.UUID;

@JsonAutoDetect(JsonMethod.NONE)
public class SlotStatusRepresentation
{
    private final UUID id;
    private final String name;
    private final URI self;
    private final String binary;
    private final String config;
    private final String status;

    public static SlotStatusRepresentation from(SlotStatus slotStatus)
    {
        if (slotStatus.getAssignment() != null) {
            return new SlotStatusRepresentation(slotStatus.getId(),
                    slotStatus.getName(),
                    slotStatus.getSelf(),
                    slotStatus.getAssignment().getBinary().toString(),
                    slotStatus.getAssignment().getConfig().toString(),
                    slotStatus.getState().toString()
            );
        }
        else {
            return new SlotStatusRepresentation(slotStatus.getId(),
                    slotStatus.getName(),
                    slotStatus.getSelf(),
                    null,
                    null,
                    slotStatus.getState().toString());
        }
    }

    @JsonCreator
    public SlotStatusRepresentation(@JsonProperty("id") UUID id,
            @JsonProperty("name") String name,
            @JsonProperty("self") URI self,
            @JsonProperty("binary") String binary,
            @JsonProperty("config") String config,
            @JsonProperty("status") String status)
    {
        this.id = id;
        this.name = name;
        this.self = self;
        this.binary = binary;
        this.config = config;
        this.status = status;
    }

    @JsonProperty
    @NotNull(message = "is missing")
    public UUID getId()
    {
        return id;
    }

    @JsonProperty
    @NotNull(message = "is missing")
    public String getName()
    {
        return name;
    }

    @JsonProperty
    @NotNull(message = "is missing")
    public URI getSelf()
    {
        return self;
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

    @JsonProperty
    public String getStatus()
    {
        return status;
    }

    public SlotStatus toSlotStatus()
    {
        if (binary != null) {
            return new SlotStatus(id, name, self, LifecycleState.valueOf(status), new Assignment(binary, config));
        }
        else {
            return new SlotStatus(id, name, self);
        }
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

        SlotStatusRepresentation that = (SlotStatusRepresentation) o;

        if (binary != null ? !binary.equals(that.binary) : that.binary != null) {
            return false;
        }
        if (config != null ? !config.equals(that.config) : that.config != null) {
            return false;
        }
        if (id != null ? !id.equals(that.id) : that.id != null) {
            return false;
        }
        if (name != null ? !name.equals(that.name) : that.name != null) {
            return false;
        }
        if (self != null ? !self.equals(that.self) : that.self != null) {
            return false;
        }
        if (status != null ? !status.equals(that.status) : that.status != null) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode()
    {
        int result = id != null ? id.hashCode() : 0;
        result = 31 * result + (name != null ? name.hashCode() : 0);
        result = 31 * result + (binary != null ? binary.hashCode() : 0);
        result = 31 * result + (config != null ? config.hashCode() : 0);
        result = 31 * result + (status != null ? status.hashCode() : 0);
        result = 31 * result + (self != null ? self.hashCode() : 0);
        return result;
    }

    @Override
    public String toString()
    {
        final StringBuffer sb = new StringBuffer();
        sb.append("SlotStatusRepresentation");
        sb.append("{id=").append(id);
        sb.append(", name='").append(name).append('\'');
        sb.append(", self=").append(self);
        sb.append(", binary='").append(binary).append('\'');
        sb.append(", config='").append(config).append('\'');
        sb.append(", status='").append(status).append('\'');
        sb.append('}');
        return sb.toString();
    }
}
