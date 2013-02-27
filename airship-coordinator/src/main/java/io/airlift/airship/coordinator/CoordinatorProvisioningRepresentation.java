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
package io.airlift.airship.coordinator;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class CoordinatorProvisioningRepresentation
{
    private final String coordinatorConfig;
    private final int coordinatorCount;
    private final String instanceType;
    private final String availabilityZone;
    private final String ami;
    private final String keyPair;
    private final String securityGroup;

    @JsonCreator
    public CoordinatorProvisioningRepresentation(
            @JsonProperty("coordinatorConfig") String coordinatorConfig,
            @JsonProperty("coordinatorCount") int coordinatorCount,
            @JsonProperty("instanceType") String instanceType,
            @JsonProperty("availabilityZone") String availabilityZone,
            @JsonProperty("ami") String ami,
            @JsonProperty("keyPair") String keyPair,
            @JsonProperty("securityGroup") String securityGroup)
    {
        this.coordinatorConfig = coordinatorConfig;
        this.coordinatorCount = coordinatorCount;
        this.instanceType = instanceType;
        this.availabilityZone = availabilityZone;
        this.ami = ami;
        this.keyPair = keyPair;
        this.securityGroup = securityGroup;
    }

    @JsonProperty
    public String getCoordinatorConfig()
    {
        return coordinatorConfig;
    }

    @JsonProperty
    public int getCoordinatorCount()
    {
        return coordinatorCount;
    }

    @JsonProperty
    public String getInstanceType()
    {
        return instanceType;
    }

    @JsonProperty
    public String getAvailabilityZone()
    {
        return availabilityZone;
    }

    @JsonProperty
    public String getAmi()
    {
        return ami;
    }

    @JsonProperty
    public String getKeyPair()
    {
        return keyPair;
    }

    @JsonProperty
    public String getSecurityGroup()
    {
        return securityGroup;
    }

    @Override
    public String toString()
    {
        final StringBuilder sb = new StringBuilder();
        sb.append("CoordinatorProvisioningRepresentation");
        sb.append("{coordinatorConfigSpec='").append(coordinatorConfig).append('\'');
        sb.append(", coordinatorCount=").append(coordinatorCount);
        sb.append(", instanceType='").append(instanceType).append('\'');
        sb.append(", availabilityZone='").append(availabilityZone).append('\'');
        sb.append(", ami='").append(ami).append('\'');
        sb.append(", keyPair='").append(keyPair).append('\'');
        sb.append(", securityGroup='").append(securityGroup).append('\'');
        sb.append('}');
        return sb.toString();
    }
}
