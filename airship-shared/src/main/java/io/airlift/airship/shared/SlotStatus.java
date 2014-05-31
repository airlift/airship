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

import com.google.common.base.Function;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;

import javax.annotation.concurrent.Immutable;

import java.net.URI;
import java.util.Map;
import java.util.UUID;

import static io.airlift.airship.shared.SlotLifecycleState.TERMINATED;
import static io.airlift.airship.shared.SlotLifecycleState.UNKNOWN;

@Immutable
public class SlotStatus
{
    public static SlotStatus createSlotStatus(UUID id,
            URI self,
            URI externalUri,
            String instanceId,
            String location,
            SlotLifecycleState state,
            Assignment assignment,
            String installPath,
            Map<String, Integer> resources)
    {
        return new SlotStatus(id, self, externalUri, instanceId, location, state, assignment, installPath, resources, null, null, null);
    }

    public static SlotStatus createSlotStatusWithExpectedState(UUID id,
            URI self,
            URI externalUri,
            String instanceId,
            String location,
            SlotLifecycleState state,
            Assignment assignment,
            String installPath,
            Map<String, Integer> resources,
            SlotLifecycleState expectedState,
            Assignment expectedAssignment,
            String statusMessage)
    {
        return new SlotStatus(id, self, externalUri, instanceId, location, state, assignment, installPath, resources, expectedState, expectedAssignment, statusMessage);
    }

    private final UUID id;
    private final URI self;
    private final URI externalUri;
    private final String instanceId;
    private final String location;
    private final Assignment assignment;
    private final SlotLifecycleState state;
    private final String version;

    private final SlotLifecycleState expectedState;
    private final Assignment expectedAssignment;

    private final String statusMessage;

    private final String installPath;

    private final Map<String, Integer> resources;

    private SlotStatus(UUID id,
            URI self,
            URI externalUri,
            String instanceId, String location,
            SlotLifecycleState state,
            Assignment assignment,
            String installPath,
            Map<String, Integer> resources,
            SlotLifecycleState expectedState,
            Assignment expectedAssignment,
            String statusMessage)
    {
        Preconditions.checkNotNull(id, "id is null");
        Preconditions.checkNotNull(location, "location is null");
        Preconditions.checkArgument(location.startsWith("/"), "location must start with a /");
        Preconditions.checkNotNull(state, "state is null");
        if (state != TERMINATED && state != UNKNOWN) {
            Preconditions.checkNotNull(assignment, "assignment is null");
        }
        Preconditions.checkNotNull(resources, "resources is null");

        this.id = id;
        this.self = self;
        this.externalUri = externalUri;
        this.instanceId = instanceId;
        this.location = location;
        this.assignment = assignment;
        this.state = state;
        this.version = VersionsUtil.createSlotVersion(id, state, assignment);
        this.installPath = installPath;
        this.expectedState = expectedState;
        this.expectedAssignment = expectedAssignment;
        this.statusMessage = statusMessage;
        this.resources = ImmutableMap.copyOf(resources);
    }


    public UUID getId()
    {
        return id;
    }

    public URI getSelf()
    {
        return self;
    }

    public URI getExternalUri()
    {
        return externalUri;
    }

    public String getInstanceId()
    {
        return instanceId;
    }

    public String getLocation()
    {
        return location;
    }

    public Assignment getAssignment()
    {
        return assignment;
    }

    public SlotLifecycleState getState()
    {
        return state;
    }

    public String getVersion()
    {
        return version;
    }

    public SlotLifecycleState getExpectedState()
    {
        return expectedState;
    }

    public Assignment getExpectedAssignment()
    {
        return expectedAssignment;
    }

    public String getStatusMessage()
    {
        return statusMessage;
    }

    public String getInstallPath()
    {
        return installPath;
    }

    public Map<String, Integer> getResources()
    {
        return resources;
    }

    public SlotStatus changeState(SlotLifecycleState state)
    {
        return createSlotStatusWithExpectedState(this.id,
                this.self,
                this.externalUri,
                this.instanceId,
                this.location,
                state,
                state == TERMINATED ? null : this.assignment,
                state == TERMINATED ? null : this.installPath,
                state == TERMINATED ? ImmutableMap.<String, Integer>of() : this.resources,
                this.expectedState,
                this.expectedAssignment,
                this.statusMessage);
    }

    public SlotStatus changeInstanceId(String instanceId)
    {
        return createSlotStatusWithExpectedState(this.id,
                this.self,
                this.externalUri,
                instanceId,
                this.location,
                state,
                state == TERMINATED ? null : this.assignment,
                state == TERMINATED ? null : this.installPath,
                state == TERMINATED ? ImmutableMap.<String, Integer>of() : this.resources,
                this.expectedState,
                this.expectedAssignment,
                this.statusMessage);
    }

    public SlotStatus changeAssignment(SlotLifecycleState state, Assignment assignment, Map<String, Integer> resources)
    {
        return createSlotStatusWithExpectedState(this.id,
                this.self,
                this.externalUri,
                this.instanceId,
                this.location,
                state,
                state == TERMINATED ? null : assignment,
                state == TERMINATED ? null : this.installPath,
                state == TERMINATED ? ImmutableMap.<String, Integer>of() : ImmutableMap.copyOf(resources),
                this.expectedState,
                this.expectedAssignment,
                this.statusMessage);
    }

    public SlotStatus changeExpectedState(SlotLifecycleState expectedState, Assignment expectedAssignment)
    {
        return createSlotStatusWithExpectedState(this.id,
                this.self,
                this.externalUri,
                this.instanceId,
                this.location,
                this.state,
                this.assignment,
                this.installPath,
                this.resources,
                expectedState,
                expectedAssignment,
                this.statusMessage);
    }

    public SlotStatus changeStatusMessage(String statusMessage)
    {
        return createSlotStatusWithExpectedState(this.id,
                this.self,
                this.externalUri,
                this.instanceId,
                this.location,
                this.state,
                this.assignment,
                this.installPath,
                this.resources,
                this.expectedState,
                this.expectedAssignment,
                statusMessage);
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

        if (assignment != null ? !assignment.equals(that.assignment) : that.assignment != null) {
            return false;
        }
        if (!id.equals(that.id)) {
            return false;
        }
        if (installPath != null ? !installPath.equals(that.installPath) : that.installPath != null) {
            return false;
        }
        if (instanceId != null ? !instanceId.equals(that.instanceId) : that.instanceId != null) {
            return false;
        }
        if (!location.equals(that.location)) {
            return false;
        }
        if (self != null ? !self.equals(that.self) : that.self != null) {
            return false;
        }
        if (externalUri != null ? !externalUri.equals(that.externalUri) : that.externalUri != null) {
            return false;
        }
        if (state != that.state) {
            return false;
        }
        if (!version.equals(that.version)) {
            return false;
        }
        if (!resources.equals(that.resources)) {
            return false;
        }
        if (expectedState != that.expectedState) {
            return false;
        }
        if (expectedAssignment != null ? !expectedAssignment.equals(that.expectedAssignment) : that.expectedAssignment != null) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode()
    {
        int result = id.hashCode();
        result = 31 * result + self.hashCode();
        result = 31 * result + externalUri.hashCode();
        result = 31 * result + instanceId.hashCode();
        result = 31 * result + location.hashCode();
        result = 31 * result + (assignment != null ? assignment.hashCode() : 0);
        result = 31 * result + state.hashCode();
        result = 31 * result + version.hashCode();
        result = 31 * result + (installPath != null ? installPath.hashCode() : 0);
        result = 31 * result + resources.hashCode();
        result = 31 * result + (expectedState != null ? expectedState.hashCode() : 0);
        result = 31 * result + (expectedAssignment != null ? expectedAssignment.hashCode() : 0);
        return result;
    }

    @Override
    public String toString()
    {
        final StringBuilder sb = new StringBuilder();
        sb.append("SlotStatus");
        sb.append("{id=").append(id);
        sb.append(", self=").append(self);
        sb.append(", externalUri=").append(externalUri);
        sb.append(", instanceId='").append(instanceId).append('\'');
        sb.append(", location='").append(location).append('\'');
        sb.append(", assignment=").append(assignment);
        sb.append(", state=").append(state);
        sb.append(", version='").append(version).append('\'');
        sb.append(", expectedState=").append(expectedState);
        sb.append(", expectedAssignment=").append(expectedAssignment);
        sb.append(", statusMessage='").append(statusMessage).append('\'');
        sb.append(", installPath='").append(installPath).append('\'');
        sb.append(", resources=").append(resources);
        sb.append('}');
        return sb.toString();
    }

    public static Function<SlotStatus, String> idGetter()
    {
        return new Function<SlotStatus, String>()
        {
            public String apply(SlotStatus input)
            {
                return input.getId().toString();
            }
        };
    }

    public static Function<SlotStatus, UUID> uuidGetter()
    {
        return new Function<SlotStatus, UUID>()
        {
            public UUID apply(SlotStatus input)
            {
                return input.getId();
            }
        };
    }

    public static Function<SlotStatus, String> locationGetter()
    {
        return new Function<SlotStatus, String>()
        {
            public String apply(SlotStatus input)
            {
                return input.getLocation();
            }
        };
    }
}
