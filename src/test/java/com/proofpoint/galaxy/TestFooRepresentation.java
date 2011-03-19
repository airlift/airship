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

import com.proofpoint.experimental.json.JsonCodec;
import com.proofpoint.experimental.json.JsonCodecBuilder;
import org.codehaus.jackson.annotate.JsonAutoDetect;
import org.codehaus.jackson.annotate.JsonCreator;
import org.codehaus.jackson.annotate.JsonMethod;
import org.codehaus.jackson.annotate.JsonProperty;
import org.testng.annotations.Test;

import java.net.URI;

import static org.testng.Assert.assertEquals;

public class TestFooRepresentation
{
    private final JsonCodec<FooRepresentation> codec = new JsonCodecBuilder().build(FooRepresentation.class);


    private final FooRepresentation expected = new FooRepresentation(URI.create("fake://uri"));

    @Test
    public void testJsonRoundTrip()
    {
        String json = codec.toJson(expected);
        FooRepresentation actual = codec.fromJson(json);
        assertEquals(actual, expected);
    }

    @JsonAutoDetect(JsonMethod.NONE)
    public static class FooRepresentation
    {
        private final URI value;

        @JsonCreator
        public FooRepresentation(@JsonProperty("value") URI string)
        {
            this.value = string;
        }

        @JsonProperty
        public URI getValue()
        {
            return value;
        }

        @Override
        public boolean equals(Object o)
        {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            FooRepresentation that = (FooRepresentation) o;

            if (value != null ? !value.equals(that.value) : that.value != null) {
                return false;
            }

            return true;
        }

        @Override
        public int hashCode()
        {
            return value != null ? value.hashCode() : 0;
        }

        @Override
        public String toString()
        {
            final StringBuffer sb = new StringBuffer();
            sb.append("FooRepresentation");
            sb.append("{value='").append(value).append('\'');
            sb.append('}');
            return sb.toString();
        }
    }
}
