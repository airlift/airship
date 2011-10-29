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

import com.google.common.base.Objects;

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
    private String location;
    private final URI uri;

    public Instance(String instanceId, String instanceType, String location, URI uri)
    {
        this.uri = uri;
        this.instanceId = checkNotNull(instanceId, "instanceId is null");
        this.instanceType = checkNotNull(instanceType, "instanceType is null");
        this.location = location;
    }

    public String getInstanceId()
    {
        return instanceId;
    }

    public String getInstanceType()
    {
        return instanceType;
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
        if (!(o instanceof Instance)) {
            return false;
        }
        Instance x = (Instance) o;
        return Objects.equal(instanceId, x.instanceId);
    }

    @Override
    public int hashCode()
    {
        return instanceId.hashCode();
    }

    @Override
    public String toString()
    {
        return Objects.toStringHelper(this)
                .add("instanceId", instanceId)
                .add("instanceType", instanceType)
                .add("location", location)
                .add("uri", uri)
                .toString();
    }
}
