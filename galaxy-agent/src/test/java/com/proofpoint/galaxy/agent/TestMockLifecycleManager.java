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
package com.proofpoint.galaxy.agent;

import com.google.common.collect.ImmutableMap;
import org.testng.annotations.BeforeMethod;

import java.io.File;
import java.util.UUID;

import static com.proofpoint.galaxy.shared.AssignmentHelper.APPLE_ASSIGNMENT;
import static com.proofpoint.galaxy.shared.AssignmentHelper.BANANA_ASSIGNMENT;

public class TestMockLifecycleManager extends AbstractLifecycleManagerTest
{
    @BeforeMethod
    protected void setUp()
            throws Exception
    {
        manager = new MockLifecycleManager();
        appleDeployment = new Deployment("apple",
                "slot",
                UUID.randomUUID(),
                "location/apple",
                new File("apple"),
                new File("apple-data"),
                APPLE_ASSIGNMENT,
                ImmutableMap.<String, Integer>of( "memory", 512));
        bananaDeployment = new Deployment("banana",
                "slot",
                UUID.randomUUID(),
                "location/banana",
                new File("banana"),
                new File("banana-data"),
                BANANA_ASSIGNMENT,
                ImmutableMap.<String, Integer>of("cpu", 1));
    }
}
