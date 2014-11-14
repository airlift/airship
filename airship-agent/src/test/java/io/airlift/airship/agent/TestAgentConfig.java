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
package io.airlift.airship.agent;

import com.google.common.collect.ImmutableMap;
import io.airlift.configuration.testing.ConfigAssertions;
import io.airlift.units.Duration;
import org.testng.annotations.Test;

import java.util.Map;
import java.util.concurrent.TimeUnit;

public class TestAgentConfig
{
    @Test
    public void testDefaults()
    {
        ConfigAssertions.assertRecordedDefaults(ConfigAssertions.recordDefaults(AgentConfig.class)
                        .setSlotsDir("slots")
                        .setResourcesFile("etc/resources.properties")
                        .setEnvironmentFile(null)
                        .setLauncherTimeout(new Duration(5, TimeUnit.SECONDS))
                        .setLauncherStopTimeout(new Duration(15, TimeUnit.SECONDS))
                        .setTarTimeout(new Duration(1, TimeUnit.MINUTES))
                        .setMaxLockWait(new Duration(1, TimeUnit.SECONDS))
        );
    }

    @Test
    public void testExplicitPropertyMappings()
    {
        Map<String, String> properties = new ImmutableMap.Builder<String, String>()
                .put("agent.slots-dir", "slots-dir")
                .put("agent.resources-file", "resources-file")
                .put("agent.environment-file", "the-environment-file")
                .put("agent.launcher-timeout", "5m")
                .put("agent.launcher-stop-timeout", "50m")
                .put("agent.tar-timeout", "10m")
                .put("agent.max-lock-wait", "1m")
                .build();

        AgentConfig expected = new AgentConfig()
                .setSlotsDir("slots-dir")
                .setResourcesFile("resources-file")
                .setEnvironmentFile("the-environment-file")
                .setLauncherTimeout(new Duration(5, TimeUnit.MINUTES))
                .setLauncherStopTimeout(new Duration(50, TimeUnit.MINUTES))
                .setTarTimeout(new Duration(10, TimeUnit.MINUTES))
                .setMaxLockWait(new Duration(1, TimeUnit.MINUTES));

        ConfigAssertions.assertFullMapping(properties, expected);
    }
}
