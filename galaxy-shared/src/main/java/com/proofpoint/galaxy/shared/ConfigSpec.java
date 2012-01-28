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

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ConfigSpec
{
    public static final String CONFIG_SPEC_REGEX = "^@(?:([^:]+):)?([^:]+):([^:]+)$";
    private static final Pattern CONFIG_SPEC_PATTERN = Pattern.compile(CONFIG_SPEC_REGEX);
    public static final String DEFAULT_CONFIG_PACKAGING = "config";

    public static MavenCoordinates parseConfigSpec(String configSpec)
    {
        Matcher matcher = CONFIG_SPEC_PATTERN.matcher(configSpec);
        if (!matcher.matches()) {
            throw new IllegalArgumentException("Invalid config spec: " + configSpec);
        }
        String groupId = matcher.group(1);
        String artifactId = matcher.group(2);
        String version = matcher.group(3);
        return createConfigSpec(groupId, artifactId, version);
    }

    public static MavenCoordinates createConfigSpec(String artifactId, String version)
    {
        return new MavenCoordinates(null, artifactId, version, DEFAULT_CONFIG_PACKAGING, null, null);
    }

    public static MavenCoordinates createConfigSpec(String groupId, String artifactId, String version)
    {
        return new MavenCoordinates(groupId, artifactId, version, DEFAULT_CONFIG_PACKAGING, null, null);
    }

    public static MavenCoordinates createConfigSpec(String groupId, String artifactId, String version, String fileVersion)
    {
        return new MavenCoordinates(groupId, artifactId, version, DEFAULT_CONFIG_PACKAGING, null, fileVersion);
    }

    public static String toConfigGAV(MavenCoordinates configSpec)
    {
        return "@"  + MavenCoordinates.toGAV(configSpec, DEFAULT_CONFIG_PACKAGING, false);
    }

    private ConfigSpec()
    {
    }
}
