/*
 * Copyright 2011 Proofpoint, Inc.
 *
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
package com.proofpoint.galaxy.coordinator;

import com.google.common.base.Joiner;

import javax.annotation.concurrent.Immutable;
import java.net.URI;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Provisioned Instance
 */
@Immutable
public class Instance
{
    private final String instanceId;
    private final String instanceType;
    private final String region;
    private final String availabilityZone;
    private String location;
    private final URI uri;

    public Instance(String region, String availabilityZone, String instanceId, String instanceType)
    {
        this(region, availabilityZone, instanceId, instanceType, null);
    }

    public Instance(String region, String availabilityZone, String instanceId, String instanceType, URI uri)
    {
        this.uri = uri;
        this.region = checkNotNull(region, "region is null");
        this.availabilityZone = checkNotNull(availabilityZone, "availabilityZone is null");
        this.instanceId = checkNotNull(instanceId, "instanceId is null");
        this.instanceType = checkNotNull(instanceType, "instanceType is null");
        location = Joiner.on('/').join("ec2", region, availabilityZone, instanceId, "agent");
    }

    public String getInstanceId()
    {
        return instanceId;
    }

    public String getInstanceType()
    {
        return instanceType;
    }

    public String getRegion()
    {
        return region;
    }

    public String getAvailabilityZone()
    {
        return availabilityZone;
    }

    public String getLocation()
    {
        return location;
    }

    public URI getUri()
    {
        return uri;
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

        Instance instance = (Instance) o;

        if (!instanceId.equals(instance.instanceId)) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode()
    {
        return instanceId.hashCode();
    }

    @Override
    public String toString()
    {
        final StringBuilder sb = new StringBuilder();
        sb.append("Ec2Location");
        sb.append("{region='").append(region).append('\'');
        sb.append(", availabilityZone='").append(availabilityZone).append('\'');
        sb.append(", instanceId='").append(instanceId).append('\'');
        sb.append(", instanceType='").append(instanceType).append('\'');
        sb.append(", location='").append(location).append('\'');
        sb.append(", uri=").append(uri);
        sb.append('}');
        return sb.toString();
    }
}
