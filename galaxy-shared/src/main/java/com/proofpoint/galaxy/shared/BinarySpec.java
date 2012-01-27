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
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;

import javax.annotation.concurrent.Immutable;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Immutable
public class BinarySpec
{
    public static final String BINARY_SPEC_REGEX = "^([^:]+):([^:]+)(?::([^:]+))?(?::([^:]+))?(?::([^:]+))?$";
    private static final Pattern BINARY_SPEC_PATTERN = Pattern.compile(BINARY_SPEC_REGEX);
    public static final String DEFAULT_PACKAGING = "tar.gz";

    private final String groupId;
    private final String artifactId;
    private final String packaging;
    private final String classifier;
    private final String version;
    private final String fileVersion;

    public static BinarySpec valueOf(String binarySpec)
    {
        Matcher matcher = BINARY_SPEC_PATTERN.matcher(binarySpec);
        if (!matcher.matches()) {
            throw new IllegalArgumentException("Invalid binary spec: " + binarySpec);
        }

        List<String> parts = ImmutableList.copyOf(Splitter.on(':').split(binarySpec));
        if (parts.size() == 5) {
            return new BinarySpec(parts.get(0), parts.get(1), parts.get(4), parts.get(2), parts.get(3));
        } else if (parts.size() == 4) {
            return new BinarySpec(parts.get(0), parts.get(1), parts.get(3), parts.get(2), null);
        } else if (parts.size() == 3) {
            return new BinarySpec(parts.get(0), parts.get(1), parts.get(2), null, null);
        } else if (parts.size() == 2) {
            return new BinarySpec(null, parts.get(0), parts.get(1), null, null);
        } else  {
            throw new IllegalArgumentException("Invalid binary spec: " + binarySpec);
        }
    }

    public BinarySpec(String groupId, String artifactId, String version)
    {
        this(groupId, artifactId, version, null, null);
    }

    public BinarySpec(String groupId, String artifactId, String version, String packaging, String classifier)
    {
        this(groupId, artifactId, version, packaging, classifier, null);
    }

    public BinarySpec(String groupId, String artifactId, String version, String packaging, String classifier, String fileVersion)
    {
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
        this.fileVersion = fileVersion;
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

    public String getFileVersion()
    {
        if (fileVersion != null) {
            return fileVersion;
        } else {
            return version;
        }
    }

    public boolean isResolved()
    {
        return groupId != null && fileVersion != null;
    }

    public boolean equalsIgnoreVersion(BinarySpec that)
    {
        if (this == that) {
            return true;
        }
        if (that == null) {
            return false;
        }

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

        return true;
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
        if (groupId != null ? !groupId.equals(that.groupId) : that.groupId != null) {
            return false;
        }
        if (!packaging.equals(that.packaging)) {
            return false;
        }
        if (!version.equals(that.version)) {
            return false;
        }
        if (fileVersion != null ? !fileVersion.equals(that.fileVersion) : that.fileVersion != null) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode()
    {
        int result = (groupId != null ? groupId.hashCode() : 0);
        result = 31 * result + artifactId.hashCode();
        result = 31 * result + packaging.hashCode();
        result = 31 * result + (classifier != null ? classifier.hashCode() : 0);
        result = 31 * result + version.hashCode();
        result = 31 * result + (fileVersion != null ? fileVersion.hashCode() : 0);
        return result;
    }

    @Override
    public String toString()
    {
        return toGAV(DEFAULT_PACKAGING, true);
    }

    public String toGAV()
    {
        return toGAV(DEFAULT_PACKAGING, false);
    }

    protected String toGAV(String defaultPackaging, boolean showFullVersion)
    {
        final StringBuilder sb = new StringBuilder();
        if (groupId != null) {
            sb.append(groupId).append(':');
        }
        sb.append(artifactId).append(':');
        if (!packaging.equals(defaultPackaging) || classifier != null) {
            sb.append(packaging).append(':');
        }
        if (classifier != null) {
            sb.append(classifier).append(':');
        }

        if (showFullVersion) {
            sb.append(version);
            if (fileVersion != null) {
                sb.append("(").append(fileVersion).append(")");
            }
        }
        else {
            if (fileVersion != null) {
                sb.append(fileVersion);
            } else {
                sb.append(version);
            }
        }

        return sb.toString();
    }
}
