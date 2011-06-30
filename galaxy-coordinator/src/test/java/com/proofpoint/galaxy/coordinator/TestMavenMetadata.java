package com.proofpoint.galaxy.coordinator;

import com.google.common.io.Resources;
import org.testng.annotations.Test;

import static com.google.common.base.Charsets.UTF_8;
import static com.proofpoint.galaxy.coordinator.MavenMetadata.unmarshalMavenMetadata;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;

public class TestMavenMetadata
{
    @Test
    public void testSnapshotVersion()
            throws Exception
    {

        String xml = Resources.toString(Resources.getResource(getClass(), "banana-maven-metadata.xml"), UTF_8);
        MavenMetadata metadata = unmarshalMavenMetadata(xml);
        assertNotNull(metadata);
        assertEquals(metadata.groupId, "food.fruit");
        assertEquals(metadata.artifactId, "banana");
        assertEquals(metadata.version, "2.0-SNAPSHOT");

        assertNotNull(metadata.versioning);

        assertNotNull(metadata.versioning.snapshot);
        assertEquals(metadata.versioning.snapshot.timestamp, "20110311.201909");
        assertEquals(metadata.versioning.snapshot.buildNumber, "1");

        assertNotNull(metadata.versioning.snapshotVersions);

        assertEquals(metadata.versioning.lastUpdated, "20110311201909");
    }

    @Test
    public void testArtifactDir()
            throws Exception
    {

        String xml = Resources.toString(Resources.getResource(getClass(), "apple-maven-metadata.xml"), UTF_8);
        MavenMetadata metadata = unmarshalMavenMetadata(xml);
        assertNotNull(metadata);
        assertEquals(metadata.groupId, "food.fruit");
        assertEquals(metadata.artifactId, "apple");

        assertEquals(metadata.versioning.latest, "2.0");
        assertEquals(metadata.versioning.release, "2.0");

        assertNotNull(metadata.versioning);

        assertNull(metadata.versioning.snapshot);
        assertNull(metadata.versioning.snapshotVersions);

        assertEquals(metadata.versioning.lastUpdated, "20110304215947");
    }
}
