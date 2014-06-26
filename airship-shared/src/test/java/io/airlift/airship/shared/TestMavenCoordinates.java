package io.airlift.airship.shared;

import io.airlift.testing.EquivalenceTester;
import org.testng.Assert;
import org.testng.annotations.Test;

public class TestMavenCoordinates
{
    @Test
    public void resolvedCoordinates()
    {
        MavenCoordinates spec = new MavenCoordinates("my.groupId", "artifactId", "version", "packaging", "classifier", "file-version");
        Assert.assertEquals(spec.getGroupId(), "my.groupId");
        Assert.assertEquals(spec.getArtifactId(), "artifactId");
        Assert.assertEquals(spec.getPackaging(), "packaging");
        Assert.assertEquals(spec.getClassifier(), "classifier");
        Assert.assertEquals(spec.getVersion(), "version");
        Assert.assertEquals(spec.getFileVersion(), "file-version");
        Assert.assertEquals(spec, spec);
        Assert.assertEquals(spec, new MavenCoordinates("my.groupId", "artifactId", "version", "packaging", "classifier", "file-version"));
        Assert.assertEquals(spec.toGAV(), "my.groupId:artifactId:packaging:classifier:file-version");
        Assert.assertEquals(spec.toString(), "my.groupId:artifactId:packaging:classifier:version(file-version)");
        Assert.assertTrue(spec.equalsIgnoreVersion(new MavenCoordinates("my.groupId", "artifactId", "version", "packaging", "classifier", "file-version")));
        Assert.assertTrue(spec.equalsIgnoreVersion(new MavenCoordinates("my.groupId", "artifactId", "version", "packaging", "classifier", null)));
        Assert.assertTrue(spec.equalsIgnoreVersion(new MavenCoordinates("my.groupId", "artifactId", "foo", "packaging", "classifier", "file-version")));
        Assert.assertTrue(spec.equalsIgnoreVersion(new MavenCoordinates("my.groupId", "artifactId", "version", "packaging", "classifier", "foo")));
        Assert.assertTrue(spec.equalsIgnoreVersion(new MavenCoordinates("my.groupId", "artifactId", "version", "packaging", "classifier", "foo")));
        Assert.assertFalse(spec.equalsIgnoreVersion(new MavenCoordinates("foo", "artifactId", "version", "packaging", "classifier", "file-version")));
        Assert.assertFalse(spec.equalsIgnoreVersion(new MavenCoordinates("my.groupId", "foo", "version", "packaging", "classifier", "file-version")));
        Assert.assertFalse(spec.equalsIgnoreVersion(new MavenCoordinates("my.groupId", "artifactId", "version", "foo", "classifier", "file-version")));
        Assert.assertFalse(spec.equalsIgnoreVersion(new MavenCoordinates("my.groupId", "artifactId", "version", "packaging", "foo", "file-version")));
    }

    @Test
    public void fullCoordinates()
    {
        MavenCoordinates spec = MavenCoordinates.fromGAV("my.groupId:artifactId:packaging:classifier:version");
        Assert.assertEquals(spec.getGroupId(), "my.groupId");
        Assert.assertEquals(spec.getArtifactId(), "artifactId");
        Assert.assertEquals(spec.getPackaging(), "packaging");
        Assert.assertEquals(spec.getClassifier(), "classifier");
        Assert.assertEquals(spec.getVersion(), "version");
        Assert.assertEquals(spec.getFileVersion(), "version");
        Assert.assertEquals(spec, spec);
        Assert.assertEquals(spec, new MavenCoordinates("my.groupId", "artifactId", "version", "packaging", "classifier", null));
        Assert.assertEquals(spec.toGAV(), "my.groupId:artifactId:packaging:classifier:version");
        Assert.assertEquals(spec.toString(), "my.groupId:artifactId:packaging:classifier:version");
    }

    @Test
    public void packagingCoordinates()
    {
        MavenCoordinates spec = MavenCoordinates.fromGAV("my.groupId:artifactId:packaging:version");
        Assert.assertEquals(spec.getGroupId(), "my.groupId");
        Assert.assertEquals(spec.getArtifactId(), "artifactId");
        Assert.assertEquals(spec.getPackaging(), "packaging");
        Assert.assertNull(spec.getClassifier());
        Assert.assertEquals(spec.getVersion(), "version");
        Assert.assertEquals(spec.getFileVersion(), "version");
        Assert.assertEquals(spec, spec);
        Assert.assertEquals(spec, new MavenCoordinates("my.groupId", "artifactId", "version", "packaging", null, null));
        Assert.assertEquals(spec.toGAV(), "my.groupId:artifactId:packaging:version");
        Assert.assertEquals(spec.toString(), "my.groupId:artifactId:packaging:version");
    }

    @Test
    public void noGroupId()
    {
        MavenCoordinates spec = new MavenCoordinates(null, "component", "2.0-SNAPSHOT", "packaging", null, "2.0-12345678.123456-1");
        Assert.assertNull(spec.getGroupId());
        Assert.assertEquals(spec.getArtifactId(), "component");
        Assert.assertEquals(spec.getPackaging(), "packaging");
        Assert.assertEquals(spec.getVersion(), "2.0-SNAPSHOT");
        Assert.assertEquals(spec.getFileVersion(), "2.0-12345678.123456-1");
        Assert.assertEquals(spec, spec);
        Assert.assertEquals(spec, new MavenCoordinates(null, "component", "2.0-SNAPSHOT", "packaging", null, "2.0-12345678.123456-1"));
        Assert.assertEquals(spec.toString(), "component:packaging:2.0-SNAPSHOT(2.0-12345678.123456-1)");
        Assert.assertEquals(spec.toGAV(), "component:packaging:2.0-12345678.123456-1");
    }

    @Test
    public void simpleCoordinates()
    {
        Assert.assertNull(MavenCoordinates.fromGAV("my.groupId:artifactId:version"));
    }

    @Test
    public void shortCoordinates()
    {
        Assert.assertNull(MavenCoordinates.fromGAV("my.groupId:artifactId:version"));
    }

    @Test
    public void bogusCoordinates()
    {
        Assert.assertNull(MavenCoordinates.fromGAV("bogus"));
        Assert.assertNull(MavenCoordinates.fromGAV("1:2:3:4:5:6:7:8:9:0"));
    }

    @Test
    public void testEquivalence()
    {
        EquivalenceTester.equivalenceTester()
                .addEquivalentGroup(
                        MavenCoordinates.fromGAV("my.group:artifactId:packaging:version"),
                        new MavenCoordinates("my.group", "artifactId", "version", "packaging", null, null))
                .addEquivalentGroup(
                        MavenCoordinates.fromGAV("my.group:artifactId:packaging:classifier:version"),
                        new MavenCoordinates("my.group", "artifactId", "version", "packaging", "classifier", null))
                .check();
    }
}
