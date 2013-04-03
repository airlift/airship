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

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMap.Builder;

import java.util.Map;

public enum SlotLifecycleState
{
    STOPPED("s"),
    RUNNING("r"),
    RESTARTING(null),
    KILLING(null),
    TERMINATED(null),
    UNKNOWN("u");

    private static Map<String, SlotLifecycleState> byName;

    static {
        Builder<String, SlotLifecycleState> builder = ImmutableMap.builder();
        for (SlotLifecycleState state : SlotLifecycleState.values()) {
            builder.put(state.name().toLowerCase(), state);
            if (state.shortName != null) {
                builder.put(state.shortName.toLowerCase(), state);
            }
        }
        byName = builder.build();
    }

    public static SlotLifecycleState lookup(String name) {
        Preconditions.checkNotNull(name, "name is null");
        return byName.get(name.toLowerCase());
    }

    private final String shortName;

    SlotLifecycleState(String shortName)
    {
        this.shortName = shortName;
    }


}
