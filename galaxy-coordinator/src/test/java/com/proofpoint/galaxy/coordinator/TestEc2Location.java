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

import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;

public class TestEc2Location
{
    @Test
    public void testParseLocation()
    {
        Ec2Location location = Ec2Location.valueOf("/ec2/us-east-1/us-east-1a/i-3ea74257/discovery");
        assertEquals(location.getRegion(), "us-east-1");
        assertEquals(location.getAvailabilityZone(), "us-east-1a");
        assertEquals(location.getInstanceId(), "i-3ea74257");
        assertEquals(location.getSlot(), "discovery");
    }

    @Test(expectedExceptions = IllegalArgumentException.class, expectedExceptionsMessageRegExp = "location must start with a slash")
    public void testNoStartingSlash()
    {
        Ec2Location.valueOf("foo");
    }

    @Test(expectedExceptions = IllegalArgumentException.class, expectedExceptionsMessageRegExp = "not an EC2 location")
    public void testNonEc2Location()
    {
        Ec2Location.valueOf("/foo");
    }

    @Test(expectedExceptions = IllegalArgumentException.class, expectedExceptionsMessageRegExp = "wrong number of parts")
    public void testTooFewParts()
    {
        Ec2Location.valueOf("/ec2/abc/xyz/123");
    }

    @Test(expectedExceptions = IllegalArgumentException.class, expectedExceptionsMessageRegExp = "wrong number of parts")
    public void testTooManyParts()
    {
        Ec2Location.valueOf("/ec2/abc/xyz/123/foo/bar");
    }
}
