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

import com.proofpoint.testing.EquivalenceTester;
import org.testng.Assert;
import org.testng.annotations.Test;

import static com.proofpoint.galaxy.shared.MavenCoordinates.DEFAULT_BINARY_PACKAGING;
import static com.proofpoint.galaxy.shared.MavenCoordinates.toBinaryGAV;

public class TestBinarySpec
{
    @Test
    public void fullBinarySpec()
    {
        MavenCoordinates spec = MavenCoordinates.fromBinaryGAV("my.groupId:artifactId:packaging:classifier:version");
        Assert.assertEquals(spec.getGroupId(), "my.groupId");
        Assert.assertEquals(spec.getArtifactId(), "artifactId");
        Assert.assertEquals(spec.getPackaging(), "packaging");
        Assert.assertEquals(spec.getClassifier(), "classifier");
        Assert.assertEquals(spec.getVersion(), "version");
        Assert.assertEquals(spec.getFileVersion(), "version");
        Assert.assertEquals(spec, spec);
        Assert.assertEquals(spec, new MavenCoordinates("my.groupId", "artifactId", "version", "packaging", "classifier", null));
        Assert.assertEquals(toBinaryGAV(spec), "my.groupId:artifactId:packaging:classifier:version");
        Assert.assertEquals(spec.toString(), "my.groupId:artifactId:packaging:classifier:version");
    }

    @Test
    public void packagingSpec()
    {
        MavenCoordinates spec = MavenCoordinates.fromBinaryGAV("my.groupId:artifactId:packaging:version");
        Assert.assertEquals(spec.getGroupId(), "my.groupId");
        Assert.assertEquals(spec.getArtifactId(), "artifactId");
        Assert.assertEquals(spec.getPackaging(), "packaging");
        Assert.assertNull(spec.getClassifier());
        Assert.assertEquals(spec.getVersion(), "version");
        Assert.assertEquals(spec.getFileVersion(), "version");
        Assert.assertEquals(spec, spec);
        Assert.assertEquals(spec, new MavenCoordinates("my.groupId", "artifactId", "version", "packaging", null, null));
        Assert.assertEquals(toBinaryGAV(spec), "my.groupId:artifactId:packaging:version");
        Assert.assertEquals(spec.toString(), "my.groupId:artifactId:packaging:version");
    }

    @Test
    public void simpleSpec()
    {
        MavenCoordinates spec = MavenCoordinates.fromBinaryGAV("my.groupId:artifactId:version");
        Assert.assertEquals(spec.getGroupId(), "my.groupId");
        Assert.assertEquals(spec.getArtifactId(), "artifactId");
        Assert.assertEquals(spec.getPackaging(), "tar.gz");
        Assert.assertNull(spec.getClassifier());
        Assert.assertEquals(spec.getVersion(), "version");
        Assert.assertEquals(spec.getFileVersion(), "version");
        Assert.assertEquals(spec, spec);
        Assert.assertEquals(spec, new MavenCoordinates("my.groupId", "artifactId", "version", "tar.gz", null, null));
        Assert.assertEquals(toBinaryGAV(spec), "my.groupId:artifactId:version");
        Assert.assertEquals(spec.toString(), "my.groupId:artifactId:tar.gz:version");
    }

    @Test
    public void shortSpec()
    {
        MavenCoordinates spec = MavenCoordinates.fromBinaryGAV("artifactId:version");
        Assert.assertNull(spec.getGroupId());
        Assert.assertEquals(spec.getArtifactId(), "artifactId");
        Assert.assertEquals(spec.getPackaging(), "tar.gz");
        Assert.assertNull(spec.getClassifier());
        Assert.assertEquals(spec.getVersion(), "version");
        Assert.assertEquals(spec.getFileVersion(), "version");
        Assert.assertEquals(spec, spec);
        Assert.assertEquals(spec, new MavenCoordinates(null, "artifactId", "version", "tar.gz", null, null));
        Assert.assertEquals(toBinaryGAV(spec), "artifactId:version");
        Assert.assertEquals(spec.toString(), "artifactId:tar.gz:version");
    }

    @Test
    public void fullBinarySpecWithDefaultPackage()
    {
        MavenCoordinates spec = MavenCoordinates.fromBinaryGAV("my.groupId:artifactId:tar.gz:classifier:version");
        Assert.assertEquals(spec.getGroupId(), "my.groupId");
        Assert.assertEquals(spec.getArtifactId(), "artifactId");
        Assert.assertEquals(spec.getPackaging(), "tar.gz");
        Assert.assertEquals(spec.getClassifier(), "classifier");
        Assert.assertEquals(spec.getVersion(), "version");
        Assert.assertEquals(spec.getFileVersion(), "version");
        Assert.assertEquals(spec, spec);
        Assert.assertEquals(spec, new MavenCoordinates("my.groupId", "artifactId", "version", "tar.gz", "classifier", null));
        Assert.assertEquals(toBinaryGAV(spec), "my.groupId:artifactId:tar.gz:classifier:version");
        Assert.assertEquals(spec.toString(), "my.groupId:artifactId:tar.gz:classifier:version");
    }

    @Test
    public void testEquivalence()
    {
        EquivalenceTester.equivalenceTester()
                .addEquivalentGroup(
                        MavenCoordinates.fromBinaryGAV("my.group:artifactId:version"),
                        new MavenCoordinates("my.group", "artifactId", "version", DEFAULT_BINARY_PACKAGING, null, null))
                .addEquivalentGroup(
                        MavenCoordinates.fromBinaryGAV("my.group:artifactId:packaging:version"),
                        new MavenCoordinates("my.group", "artifactId", "version", "packaging", null, null))
                .addEquivalentGroup(
                        MavenCoordinates.fromBinaryGAV("my.group:artifactId:packaging:classifier:version"),
                        new MavenCoordinates("my.group", "artifactId", "version", "packaging", "classifier", null))
                .check();
    }
}
