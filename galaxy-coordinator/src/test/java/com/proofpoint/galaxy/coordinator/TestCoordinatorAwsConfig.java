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

import com.google.common.collect.ImmutableMap;
import com.proofpoint.configuration.testing.ConfigAssertions;
import org.testng.annotations.Test;

import java.util.Map;

public class TestCoordinatorAwsConfig
{
    @Test
    public void testDefaults()
    {
        ConfigAssertions.assertRecordedDefaults(ConfigAssertions.recordDefaults(CoordinatorAwsConfig.class)
                .setAwsAccessKey(null)
                .setAwsSecretKey(null)
                .setAwsAgentAmi(null)
                .setAwsAgentKeypair(null)
                .setAwsAgentSecurityGroup(null)
                .setAwsAgentDefaultInstanceType(null)
        );
    }

    @Test
    public void testExplicitPropertyMappings()
    {
        Map<String, String> properties = new ImmutableMap.Builder<String, String>()
                .put("coordinator.aws.access-key", "my-access-key")
                .put("coordinator.aws.secret-key", "my-secret-key")
                .put("coordinator.aws.agent.ami", "ami-0123abcd")
                .put("coordinator.aws.agent.keypair", "keypair")
                .put("coordinator.aws.agent.security-group", "default")
                .put("coordinator.aws.agent.default-instance-type", "t1.micro")
                .build();

        CoordinatorAwsConfig expected = new CoordinatorAwsConfig()
                .setAwsAccessKey("my-access-key")
                .setAwsSecretKey("my-secret-key")
                .setAwsAgentAmi("ami-0123abcd")
                .setAwsAgentKeypair("keypair")
                .setAwsAgentSecurityGroup("default")
                .setAwsAgentDefaultInstanceType("t1.micro");

        ConfigAssertions.assertFullMapping(properties, expected);
    }
}
