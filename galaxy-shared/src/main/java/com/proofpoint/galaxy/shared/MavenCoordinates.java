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

import com.google.common.base.Objects;
import com.google.common.base.Preconditions;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

@Immutable
public class MavenCoordinates
{
    private final String groupId;
    private final String artifactId;
    private final String packaging;
    private final String classifier;
    private final String version;
    private final String fileVersion;

    public MavenCoordinates(String groupId, String artifactId, String version, String packaging, String classifier, String fileVersion)
    {
        Preconditions.checkNotNull(artifactId, "artifactId is null");
        Preconditions.checkNotNull(version, "version is null");
        Preconditions.checkNotNull(packaging, "packaging is null");

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

    public boolean equalsIgnoreVersion(MavenCoordinates that)
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

        MavenCoordinates that = (MavenCoordinates) o;

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
        return toGAV(this, null, true);
    }

    public static String toGAV(MavenCoordinates binarySpec, @Nullable String defaultPackaging, boolean showFullVersion)
    {
        final StringBuilder sb = new StringBuilder();
        if (binarySpec.groupId != null) {
            sb.append(binarySpec.groupId).append(':');
        }
        sb.append(binarySpec.artifactId).append(':');
        if (!Objects.equal(binarySpec.packaging, defaultPackaging) || binarySpec.classifier != null) {
            sb.append(binarySpec.packaging).append(':');
        }
        if (binarySpec.classifier != null) {
            sb.append(binarySpec.classifier).append(':');
        }

        if (showFullVersion) {
            sb.append(binarySpec.version);
            if (binarySpec.fileVersion != null) {
                sb.append("(").append(binarySpec.fileVersion).append(")");
            }
        }
        else {
            if (binarySpec.fileVersion != null) {
                sb.append(binarySpec.fileVersion);
            } else {
                sb.append(binarySpec.version);
            }
        }

        return sb.toString();
    }
}
