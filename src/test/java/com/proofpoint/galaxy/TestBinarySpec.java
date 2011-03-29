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

import com.proofpoint.testing.EquivalenceTester;
import org.testng.Assert;
import org.testng.annotations.Test;

import static java.util.Arrays.asList;
import static com.proofpoint.galaxy.BinarySpec.DEFAULT_PACKAGING;

public class TestBinarySpec
{
    @Test
    public void fullBinarySpec()
    {
        BinarySpec spec = BinarySpec.valueOf("my.groupId:artifactId:packaging:classifier:version");
        Assert.assertEquals(spec.getGroupId(), "my.groupId");
        Assert.assertEquals(spec.getArtifactId(), "artifactId");
        Assert.assertEquals(spec.getPackaging(), "packaging");
        Assert.assertEquals(spec.getClassifier(), "classifier");
        Assert.assertEquals(spec.getVersion(), "version");
        Assert.assertEquals(spec, spec);
        Assert.assertEquals(spec, new BinarySpec("my.groupId", "artifactId", "version", "packaging", "classifier"));
        Assert.assertEquals(spec.toString(), "my.groupId:artifactId:packaging:classifier:version");
    }

    @Test
    public void packagingSpec()
    {
        BinarySpec spec = BinarySpec.valueOf("my.groupId:artifactId:packaging:version");
        Assert.assertEquals(spec.getGroupId(), "my.groupId");
        Assert.assertEquals(spec.getArtifactId(), "artifactId");
        Assert.assertEquals(spec.getPackaging(), "packaging");
        Assert.assertNull(spec.getClassifier());
        Assert.assertEquals(spec.getVersion(), "version");
        Assert.assertEquals(spec, spec);
        Assert.assertEquals(spec, new BinarySpec("my.groupId", "artifactId", "version", "packaging", null));
        Assert.assertEquals(spec.toString(), "my.groupId:artifactId:packaging:version");
    }

    @Test
    public void simpleSpec()
    {
        BinarySpec spec = BinarySpec.valueOf("my.groupId:artifactId:version");
        Assert.assertEquals(spec.getGroupId(), "my.groupId");
        Assert.assertEquals(spec.getArtifactId(), "artifactId");
        Assert.assertEquals(spec.getPackaging(), "tar.gz");
        Assert.assertNull(spec.getClassifier());
        Assert.assertEquals(spec.getVersion(), "version");
        Assert.assertEquals(spec, spec);
        Assert.assertEquals(spec, new BinarySpec("my.groupId", "artifactId", "version", "tar.gz", null));
        Assert.assertEquals(spec.toString(), "my.groupId:artifactId:version");
    }

    @Test
    public void fullBinarySpecWithDefaultPackage()
    {
        BinarySpec spec = BinarySpec.valueOf("my.groupId:artifactId:tar.gz:classifier:version");
        Assert.assertEquals(spec.getGroupId(), "my.groupId");
        Assert.assertEquals(spec.getArtifactId(), "artifactId");
        Assert.assertEquals(spec.getPackaging(), "tar.gz");
        Assert.assertEquals(spec.getClassifier(), "classifier");
        Assert.assertEquals(spec.getVersion(), "version");
        Assert.assertEquals(spec, spec);
        Assert.assertEquals(spec, new BinarySpec("my.groupId", "artifactId", "version", "tar.gz", "classifier"));
        Assert.assertEquals(spec.toString(), "my.groupId:artifactId:tar.gz:classifier:version");
    }

    @Test
    public void testEquivalence()
    {
        EquivalenceTester.check(
                asList(BinarySpec.valueOf("my.group:artifactId:version"),
                        new BinarySpec("my.group", "artifactId", "version"),
                        new BinarySpec("my.group", "artifactId", "version", DEFAULT_PACKAGING, null)),
                asList(BinarySpec.valueOf("my.group:artifactId:packaging:version"), new BinarySpec("my.group", "artifactId", "version", "packaging", null)),
                asList(BinarySpec.valueOf("my.group:artifactId:packaging:classifier:version"), new BinarySpec("my.group", "artifactId", "version", "packaging", "classifier")));
    }
}
