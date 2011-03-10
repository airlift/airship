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
import com.google.common.io.Resources;
import com.proofpoint.experimental.json.JsonCodec;
import com.proofpoint.experimental.json.JsonCodecBuilder;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;

public class TestAssignmentRepresentation
{
    private final JsonCodec<AssignmentRepresentation> codec = new JsonCodecBuilder().build(AssignmentRepresentation.class);

    @Test
    public void testJsonRoundTrip()
    {
        AssignmentRepresentation expected = new AssignmentRepresentation("fruit:apple:1.0", "@prod:apple:1.0");
        String json = codec.toJson(expected);
        AssignmentRepresentation actual = codec.fromJson(json);
        assertEquals(actual, expected);
    }

    @Test
    public void testJsonDecode()
            throws Exception
    {
        AssignmentRepresentation expected = new AssignmentRepresentation("fruit:apple:1.0", "@prod:apple:1.0");

        String json = Resources.toString(Resources.getResource("assignment.json"), Charsets.UTF_8);
        AssignmentRepresentation actual = codec.fromJson(json);

        assertEquals(actual, expected);
    }
}
