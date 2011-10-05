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
import com.google.common.base.Objects;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;

import javax.annotation.concurrent.Immutable;
import java.net.URI;
import java.util.List;

import static com.google.common.base.Objects.equal;
import static com.google.common.base.Preconditions.checkNotNull;

/**
 * EC2 Galaxy location: {@code /ec2/region/zone/instance/slot}
 */
@Immutable
public class Ec2Location
{
    private final String region;
    private final String availabilityZone;
    private final String instanceId;
    private final String slot;
    private final URI uri;

    public Ec2Location(String region, String availabilityZone, String instanceId, String slot)
    {
        this(region, availabilityZone, instanceId, slot, null);
    }

    public Ec2Location(String region, String availabilityZone, String instanceId, String slot, URI uri)
    {
        this.uri = uri;
        this.region = checkNotNull(region, "region is null");
        this.availabilityZone = checkNotNull(availabilityZone, "availabilityZone is null");
        this.instanceId = checkNotNull(instanceId, "instanceId is null");
        this.slot = checkNotNull(slot, "slot is null");
    }

    public String getRegion()
    {
        return region;
    }

    public String getAvailabilityZone()
    {
        return availabilityZone;
    }

    public String getInstanceId()
    {
        return instanceId;
    }

    public String getSlot()
    {
        return slot;
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
        if (!(o instanceof Ec2Location)) {
            return false;
        }
        Ec2Location x = (Ec2Location) o;
        return equal(region, x.region) &&
                equal(availabilityZone, x.availabilityZone) &&
                equal(instanceId, x.instanceId) &&
                equal(slot, x.slot);
    }

    @Override
    public int hashCode()
    {
        return Objects.hashCode(region, availabilityZone, instanceId, slot);
    }

    @Override
    public String toString()
    {
        return Joiner.on('/').join("ec2", region, availabilityZone, instanceId, slot);
    }

    /**
     * Parse an EC2 location string into an {@link Ec2Location}.
     *
     * @param location the location string
     * @return the parsed location
     * @throws IllegalArgumentException if the location string is invalid
     */
    public static Ec2Location valueOf(String location)
            throws IllegalArgumentException
    {
        if (!location.startsWith("/")) {
            throw new IllegalArgumentException("location must start with a slash");
        }
        location = location.substring(1);
        List<String> parts = ImmutableList.copyOf(Splitter.on('/').split(location).iterator());
        if (!parts.get(0).equals("ec2")) {
            throw new IllegalArgumentException("not an EC2 location");
        }
        if (parts.size() != 5) {
            throw new IllegalArgumentException("wrong number of parts");
        }
        return new Ec2Location(parts.get(1), parts.get(2), parts.get(3), parts.get(4));
    }
}
