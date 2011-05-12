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
package com.proofpoint.galaxy.coordinator;

import com.google.common.base.Charsets;
import com.google.common.io.Resources;
import com.proofpoint.json.JsonCodec;
import com.proofpoint.galaxy.shared.AssignmentRepresentation;
import org.testng.annotations.Test;

import static com.proofpoint.json.JsonCodec.jsonCodec;
import static com.proofpoint.galaxy.shared.AssignmentHelper.APPLE_ASSIGNMENT;
import static org.testng.Assert.assertEquals;

public class TestAssignmentRepresentation
{
    private final JsonCodec<AssignmentRepresentation> codec = jsonCodec(AssignmentRepresentation.class);

    private final AssignmentRepresentation expected = new AssignmentRepresentation(
            APPLE_ASSIGNMENT.getBinary().toString(),
            APPLE_ASSIGNMENT.getConfig().toString());

    @Test
    public void testJsonRoundTrip()
    {
        String json = codec.toJson(expected);
        AssignmentRepresentation actual = codec.fromJson(json);
        assertEquals(actual, expected);
    }

    @Test
    public void testJsonDecode()
            throws Exception
    {
        String json = Resources.toString(Resources.getResource("assignment.json"), Charsets.UTF_8);
        AssignmentRepresentation actual = codec.fromJson(json);

        assertEquals(actual, expected);
    }
}
