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
package com.proofpoint.galaxy;

import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.Resources;
import com.proofpoint.experimental.json.JsonCodec;
import com.proofpoint.experimental.json.JsonCodecBuilder;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;

public class TestDeploymentRepresentation
{
    private final JsonCodec<DeploymentRepresentation> codec = new JsonCodecBuilder().build(DeploymentRepresentation.class);

    private final AssignmentRepresentation assignmentRepresentation = new AssignmentRepresentation("fruit:apple:1.0",
            "fetch://binary.tar.gz",
            "@prod:apple:1.0",
            ImmutableMap.<String,String>builder()
                    .put("etc/config.properties", "fetch://config.properties")
                    .put("readme.txt", "fetch://readme.txt")
                    .build()

            );

    private final DeploymentRepresentation expected = new DeploymentRepresentation("deployment1", assignmentRepresentation);

    @Test
    public void testJsonRoundTrip()
    {
        String json = codec.toJson(expected);
        DeploymentRepresentation actual = codec.fromJson(json);
        assertEquals(actual, expected);
    }

    @Test
    public void testJsonDecode()
            throws Exception
    {
        String json = Resources.toString(Resources.getResource("deployment.json"), Charsets.UTF_8);
        DeploymentRepresentation actual = codec.fromJson(json);

        assertEquals(actual, expected);
    }
}
