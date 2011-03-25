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

import com.google.common.base.Joiner;
import com.google.common.collect.HashMultiset;

import java.util.Collection;

import static org.testng.Assert.fail;

public class ExtraAssertions
{
    private static final Joiner ERROR_JOINER = Joiner.on("\n        ").useForNull("<null>");

    public static void assertEqualsNoOrder(Iterable<?> actual, Iterable<?> expected)
    {
        if (actual == expected) {
            return;
        }

        if (actual == null || expected == null) {
            fail("Collections not equal: expected: " + expected + " and actual: " + actual);
        }

        if (!HashMultiset.create(actual).equals(HashMultiset.create(expected))) {
            fail("Collections differ:\n    expected \n        " + ERROR_JOINER.join(expected) + "\n    actual:\n        " + ERROR_JOINER.join(actual));
        }
    }
}
