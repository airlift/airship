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

import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.Resources;
import com.proofpoint.experimental.json.JsonCodec;
import com.proofpoint.galaxy.shared.AssignmentRepresentation;
import com.proofpoint.galaxy.shared.InstallationRepresentation;
import org.testng.annotations.Test;

import static com.proofpoint.experimental.json.JsonCodec.jsonCodec;
import static com.proofpoint.galaxy.shared.AssignmentHelper.APPLE_ASSIGNMENT;
import static org.testng.Assert.assertEquals;

public class TestInstallationRepresentation
{
    private final JsonCodec<InstallationRepresentation> codec = jsonCodec(InstallationRepresentation.class);

    private final InstallationRepresentation expected = new InstallationRepresentation(
            AssignmentRepresentation.from(APPLE_ASSIGNMENT),
            "fetch://binary.tar.gz",
            ImmutableMap.<String,String>builder()
                    .put("etc/config.properties", "fetch://config.properties")
                    .put("readme.txt", "fetch://readme.txt")
                    .build()

    );

    @Test
    public void testJsonRoundTrip()
    {
        String json = codec.toJson(expected);
        InstallationRepresentation actual = codec.fromJson(json);
        assertEquals(actual, expected);
    }

    @Test
    public void testJsonDecode()
            throws Exception
    {
        String json = Resources.toString(Resources.getResource("installation.json"), Charsets.UTF_8);
        InstallationRepresentation actual = codec.fromJson(json);

        assertEquals(actual, expected);
    }
}
