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

import com.google.common.base.Preconditions;

import javax.annotation.concurrent.Immutable;
import java.net.URI;
import java.util.UUID;

import static com.proofpoint.galaxy.LifecycleState.UNASSIGNED;

@Immutable
public class SlotStatus
{
    private final UUID id;
    private final String name;
    private final URI self;
    private final BinarySpec binary;
    private final ConfigSpec config;
    private final LifecycleState state;

    public SlotStatus(UUID id, String name, URI self)
    {
        Preconditions.checkNotNull(id, "id is null");
        Preconditions.checkNotNull(name, "name is null");
        Preconditions.checkNotNull(self, "self is null");

        this.id = id;
        this.name = name;
        this.self = self;
        this.binary = null;
        this.config = null;
        this.state = UNASSIGNED;
    }

    public SlotStatus(UUID id, String name, URI self, LifecycleState state)
    {
        Preconditions.checkNotNull(id, "id is null");
        Preconditions.checkNotNull(name, "name is null");
        Preconditions.checkNotNull(self, "self is null");
        Preconditions.checkNotNull(state, "state is null");

        this.id = id;
        this.name = name;
        this.self = self;
        this.binary = null;
        this.config = null;
        this.state = state;
    }

    public SlotStatus(UUID id, String name, URI self, BinarySpec binary, ConfigSpec config, LifecycleState state)
    {
        Preconditions.checkNotNull(id, "id is null");
        Preconditions.checkNotNull(name, "name is null");
        Preconditions.checkNotNull(self, "self is null");
        Preconditions.checkNotNull(binary, "binary is null");
        Preconditions.checkNotNull(config, "config is null");
        Preconditions.checkNotNull(state, "state is null");

        this.id = id;
        this.name = name;
        this.self = self;
        this.binary = binary;
        this.config = config;
        this.state = state;
    }

    public UUID getId()
    {
        return id;
    }

    public String getName()
    {
        return name;
    }

    public URI getSelf()
    {
        return self;
    }

    public BinarySpec getBinary()
    {
        return binary;
    }

    public ConfigSpec getConfig()
    {
        return config;
    }

    public LifecycleState getState()
    {
        return state;
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

        SlotStatus that = (SlotStatus) o;

        if (binary != null ? !binary.equals(that.binary) : that.binary != null) {
            return false;
        }
        if (config != null ? !config.equals(that.config) : that.config != null) {
            return false;
        }
        if (!id.equals(that.id)) {
            return false;
        }
        if (!name.equals(that.name)) {
            return false;
        }
        if (!self.equals(that.self)) {
            return false;
        }
        if (state != that.state) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode()
    {
        int result = id.hashCode();
        result = 31 * result + name.hashCode();
        result = 31 * result + self.hashCode();
        result = 31 * result + (binary != null ? binary.hashCode() : 0);
        result = 31 * result + (config != null ? config.hashCode() : 0);
        result = 31 * result + state.hashCode();
        return result;
    }

    @Override
    public String toString()
    {
        final StringBuffer sb = new StringBuffer();
        sb.append("SlotStatus");
        sb.append("{id=").append(id);
        sb.append(", name='").append(name).append('\'');
        sb.append(", self=").append(self);
        sb.append(", binary=").append(binary);
        sb.append(", config=").append(config);
        sb.append(", state=").append(state);
        sb.append('}');
        return sb.toString();
    }
}
