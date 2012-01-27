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

import javax.annotation.concurrent.Immutable;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Immutable
public class ConfigSpec extends BinarySpec
{
    public static final String CONFIG_SPEC_REGEX = "^@(?:([^:]+):)?([^:]+):([^:]+)$";
    private static final Pattern CONFIG_SPEC_PATTERN = Pattern.compile(CONFIG_SPEC_REGEX);
    public static final String DEFAULT_PACKAGING = "config";

    public static ConfigSpec valueOf(String configSpec)
    {
        Matcher matcher = CONFIG_SPEC_PATTERN.matcher(configSpec);
        if (!matcher.matches()) {
            throw new IllegalArgumentException("Invalid config spec: " + configSpec);
        }
        String groupId = matcher.group(1);
        String artifactId = matcher.group(2);
        String version = matcher.group(3);
        return new ConfigSpec(groupId, artifactId, version);
    }

    public ConfigSpec(String artifactId, String version)
    {
        super(null, artifactId, version, DEFAULT_PACKAGING, null);
    }

    public ConfigSpec(String groupId, String artifactId, String version)
    {
        this(groupId, artifactId, version, null);
    }

    public ConfigSpec(String groupId, String artifactId, String version, String fileVersion)
    {
        super(groupId, artifactId, version, DEFAULT_PACKAGING, null, fileVersion);
    }

    @Override
    public String toString()
    {
        return "@" + toGAV(DEFAULT_PACKAGING, true);
    }

    public String toGAV()
    {
        return "@" + toGAV(DEFAULT_PACKAGING, false);
    }
}
