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

import com.google.common.base.Preconditions;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class BinarySpec
{
    public static final String BINARY_SPEC_REGEX = "^([^:]+):([^:]+)(?::([^:]+))?(?::([^:]+))?:([^:]+)$";
    private static final Pattern BINARY_SPEC_PATTERN = Pattern.compile(BINARY_SPEC_REGEX);
    public static final String DEFAULT_PACKAGING = "tar.gz";

    private final String groupId;
    private final String artifactId;
    private final String packaging;
    private final String classifier;
    private final String version;

    public static BinarySpec valueOf(String binarySpec)
    {
        Matcher matcher = BINARY_SPEC_PATTERN.matcher(binarySpec);
        if (!matcher.matches()) {
            throw new IllegalArgumentException("Invalid binary spec: " + binarySpec);
        }
        String groupId = matcher.group(1);
        String artifactId = matcher.group(2);
        String packaging = matcher.group(3);
        String classifier = matcher.group(4);
        String version = matcher.group(5);
        return new BinarySpec(groupId, artifactId, version, packaging, classifier);
    }

    public BinarySpec(String groupId, String artifactId, String version)
    {
        this(groupId, artifactId, version, null, null);
    }

    public BinarySpec(String groupId, String artifactId, String version, String packaging, String classifier)
    {
        Preconditions.checkNotNull(groupId, "groupId is null");
        Preconditions.checkNotNull(artifactId, "artifactId is null");
        Preconditions.checkNotNull(version, "version is null");

        if (packaging == null) {
            packaging = DEFAULT_PACKAGING;
        }

        this.groupId = groupId;
        this.artifactId = artifactId;
        this.packaging = packaging;
        this.classifier = classifier;
        this.version = version;
    }

    public String getGroupId()
    {
        return groupId;
    }

    public String getArtifactId()
    {
        return artifactId;
    }

    public String getPackaging()
    {
        return packaging;
    }

    public String getClassifier()
    {
        return classifier;
    }

    public String getVersion()
    {
        return version;
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

        BinarySpec that = (BinarySpec) o;

        if (!artifactId.equals(that.artifactId)) {
            return false;
        }
        if (classifier != null ? !classifier.equals(that.classifier) : that.classifier != null) {
            return false;
        }
        if (!groupId.equals(that.groupId)) {
            return false;
        }
        if (!packaging.equals(that.packaging)) {
            return false;
        }
        if (!version.equals(that.version)) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode()
    {
        int result = groupId.hashCode();
        result = 31 * result + artifactId.hashCode();
        result = 31 * result + packaging.hashCode();
        result = 31 * result + (classifier != null ? classifier.hashCode() : 0);
        result = 31 * result + version.hashCode();
        return result;
    }

    @Override
    public String toString()
    {
        final StringBuffer sb = new StringBuffer();
        sb.append(groupId).append(':');
        sb.append(artifactId).append(':');
        if (!packaging.equals(DEFAULT_PACKAGING)) {
            sb.append(packaging).append(':');
        }
        if (classifier != null) {
            sb.append(classifier).append(':');
        }
        sb.append(version);
        return sb.toString();
    }
}
