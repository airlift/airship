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
package com.proofpoint.galaxy.shared;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMap.Builder;

import java.util.Map;

public enum LifecycleState
{
    STOPPED("s"),
    RUNNING("r"),
    UNASSIGNED(null),
    UNKNOWN("u");

    private static Map<String, LifecycleState> byName;

    static {
        Builder<String, LifecycleState> builder = ImmutableMap.builder();
        for (LifecycleState state : LifecycleState.values()) {
            builder.put(state.name().toLowerCase(), state);
            if (state.shortName != null) {
                builder.put(state.shortName.toLowerCase(), state);
            }
        }
        byName = builder.build();
    }

    public static LifecycleState lookup(String name) {
        Preconditions.checkNotNull(name, "name is null");
        return byName.get(name.toLowerCase());
    }

    private final String shortName;

    LifecycleState(String shortName)
    {
        this.shortName = shortName;
    }


}
