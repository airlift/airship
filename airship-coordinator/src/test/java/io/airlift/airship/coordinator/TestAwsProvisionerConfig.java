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
package io.airlift.airship.coordinator;

import com.google.common.collect.ImmutableMap;
import io.airlift.configuration.testing.ConfigAssertions;
import io.airlift.units.Duration;
import org.testng.annotations.Test;

import java.util.Map;
import java.util.concurrent.TimeUnit;

public class TestAwsProvisionerConfig
{
    @Test
    public void testDefaults()
    {
        ConfigAssertions.assertRecordedDefaults(ConfigAssertions.recordDefaults(AwsProvisionerConfig.class)
                .setAirshipVersion(null)
                .setAgentDefaultConfig(null)
                .setAwsCredentialsFile("persist/aws-credentials.properties")
                .setAwsCoordinatorAmi("ami-27b7744e")
                .setAwsCoordinatorKeypair("keypair")
                .setAwsCoordinatorSecurityGroup("default")
                .setAwsCoordinatorDefaultInstanceType("t1.micro")
                .setAwsAgentAmi("ami-27b7744e")
                .setAwsAgentKeypair("keypair")
                .setAwsAgentSecurityGroup("default")
                .setAwsAgentDefaultInstanceType("t1.micro")
                .setAwsEndpoint(null)
                .setS3KeystoreBucket(null)
                .setS3KeystorePath(null)
                .setS3KeystoreRefreshInterval(new Duration(10, TimeUnit.SECONDS))
        );
    }

    @Test
    public void testExplicitPropertyMappings()
    {
        Map<String, String> properties = new ImmutableMap.Builder<String, String>()
                .put("airship.version", "99.9")
                .put("coordinator.agent.default-config", "agent:config:1")
                .put("coordinator.aws.credentials-file", "aws.credentials")
                .put("coordinator.aws.endpoint", "http://ytmnd.com")
                .put("coordinator.aws.coordinator.ami", "c-ami-0123abcd")
                .put("coordinator.aws.coordinator.keypair", "c-keypair")
                .put("coordinator.aws.coordinator.security-group", "c-default")
                .put("coordinator.aws.coordinator.default-instance-type", "c-t1.micro")
                .put("coordinator.aws.agent.ami", "a-ami-0123abcd")
                .put("coordinator.aws.agent.keypair", "a-keypair")
                .put("coordinator.aws.agent.security-group", "a-default")
                .put("coordinator.aws.agent.default-instance-type", "a-t1.micro")
                .put("coordinator.aws.s3-keystore.bucket", "bucket")
                .put("coordinator.aws.s3-keystore.path", "path")
                .put("coordinator.aws.s3-keystore.refresh", "30s")
                .build();

        AwsProvisionerConfig expected = new AwsProvisionerConfig()
                .setAirshipVersion("99.9")
                .setAgentDefaultConfig("agent:config:1")
                .setAwsCredentialsFile("aws.credentials")
                .setAwsEndpoint("http://ytmnd.com")
                .setAwsCoordinatorAmi("c-ami-0123abcd")
                .setAwsCoordinatorKeypair("c-keypair")
                .setAwsCoordinatorSecurityGroup("c-default")
                .setAwsCoordinatorDefaultInstanceType("c-t1.micro")
                .setAwsAgentAmi("a-ami-0123abcd")
                .setAwsAgentKeypair("a-keypair")
                .setAwsAgentSecurityGroup("a-default")
                .setAwsAgentDefaultInstanceType("a-t1.micro")
                .setS3KeystoreBucket("bucket")
                .setS3KeystorePath("path")
                .setS3KeystoreRefreshInterval(new Duration(30, TimeUnit.SECONDS));

        ConfigAssertions.assertFullMapping(properties, expected);
    }
}
