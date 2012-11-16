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

import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.Resources;
import io.airlift.json.JsonCodec;
import org.testng.annotations.Test;

import java.net.URI;
import java.util.UUID;

import static io.airlift.airship.shared.AgentLifecycleState.ONLINE;
import static io.airlift.airship.shared.AssignmentHelper.APPLE_ASSIGNMENT;
import static io.airlift.airship.shared.AssignmentHelper.BANANA_ASSIGNMENT;
import static io.airlift.airship.shared.SlotLifecycleState.STOPPED;
import static io.airlift.json.JsonCodec.jsonCodec;
import static org.testng.Assert.assertEquals;

public class TestAgentStatusRepresentation
{
    private final JsonCodec<AgentStatusRepresentation> codec = jsonCodec(AgentStatusRepresentation.class);

    private final AgentStatusRepresentation expected = new AgentStatusRepresentation(
            "44444444-4444-4444-4444-444444444444",
            "4444",
            "instanceId",
            ONLINE,
            URI.create("internal://agent"),
            URI.create("external://agent"),
            "/test/unknown/location",
            "/unknown/location",
            "instance.type",
            ImmutableList.of(
                    new SlotStatusRepresentation(UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"),
                            null,
                            URI.create("internal://apple"),
                            URI.create("external://apple"),
                            "instance",
                            "/test/location/apple",
                            "/location/apple",
                            APPLE_ASSIGNMENT.getBinary(),
                            APPLE_ASSIGNMENT.getBinary(),
                            APPLE_ASSIGNMENT.getConfig(),
                            APPLE_ASSIGNMENT.getConfig(),
                            STOPPED.toString(),
                            "abc",
                            null,
                            "/apple",
                            ImmutableMap.<String, Integer>of(),
                            null,
                            null,
                            null),
                    new SlotStatusRepresentation(UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"),
                            null,
                            URI.create("internal://banana"),
                            URI.create("external://banana"),
                            "instance",
                            "/test/location/banana",
                            "/location/banana",
                            BANANA_ASSIGNMENT.getBinary(),
                            BANANA_ASSIGNMENT.getBinary(),
                            BANANA_ASSIGNMENT.getConfig(),
                            BANANA_ASSIGNMENT.getConfig(),
                            STOPPED.toString(),
                            "abc",
                            null,
                            "/banana",
                            ImmutableMap.<String, Integer>of(),
                            null,
                            null,
                            null)),
            ImmutableMap.of("cpu", 8, "memory", 1024),
            "agent-version"
    );

    @Test
    public void testJsonRoundTrip()
    {
        String json = codec.toJson(expected);
        AgentStatusRepresentation actual = codec.fromJson(json);
        assertEquals(actual, expected);
    }

    @Test
    public void testJsonDecode()
            throws Exception
    {
        String json = Resources.toString(Resources.getResource("agent-status.json"), Charsets.UTF_8);
        AgentStatusRepresentation actual = codec.fromJson(json);

        assertEquals(actual, expected);
        assertEquals(actual.getAgentId(), expected.getAgentId());
        assertEquals(actual.getShortAgentId(), expected.getShortAgentId());
        assertEquals(actual.getInstanceId(), expected.getInstanceId());
        assertEquals(actual.getSelf(), expected.getSelf());
        assertEquals(actual.getState(), expected.getState());
        assertEquals(actual.getInstanceType(), expected.getInstanceType());
        assertEquals(actual.getResources(), expected.getResources());
        assertEquals(actual.getLocation(), expected.getLocation());
        assertEquals(actual.getShortLocation(), expected.getShortLocation());
        assertEquals(actual.getSlots(), expected.getSlots());
        assertEquals(actual.getVersion(), expected.getVersion());
    }
}
