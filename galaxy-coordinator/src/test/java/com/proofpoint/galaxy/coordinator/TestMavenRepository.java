package com.proofpoint.galaxy.coordinator;

import com.proofpoint.galaxy.shared.MavenCoordinates;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.net.URI;

import static com.proofpoint.galaxy.shared.BinarySpec.createBinarySpec;
import static com.proofpoint.galaxy.shared.BinarySpec.parseBinarySpec;

public class TestMavenRepository
{
    // This repo extends the MavenRepository so we can use it for testing
    private TestingRepository repo;

    @BeforeMethod
    public void setUp()
            throws Exception
    {
        repo = new TestingRepository();
    }

    @AfterMethod
    public void tearDown()
            throws Exception
    {
        repo.destroy();
    }

    @Test
    public void resolveAbsoluteSpec()
            throws Exception
    {
        MavenCoordinates binarySpec = parseBinarySpec("food.fruit:apple:1.0");
        Assert.assertEquals(
                repo.resolve(binarySpec),
                createBinarySpec("food.fruit", "apple", "1.0", null, null, "1.0"));

        URI uri = repo.getUri(binarySpec);
        Assert.assertTrue(uri.getPath().endsWith("/food/fruit/apple/1.0/apple-1.0.tar.gz"));
    }

    @Test
    public void resolveDefaultGroupId()
            throws Exception
    {
        MavenCoordinates binarySpec = parseBinarySpec("apple:1.0");
        Assert.assertEquals(
                repo.resolve(binarySpec),
                createBinarySpec("food.fruit", "apple", "1.0", null, null, "1.0"));

        URI uri = repo.getUri(binarySpec);
        Assert.assertTrue(uri.getPath().endsWith("/food/fruit/apple/1.0/apple-1.0.tar.gz"));
    }

    @Test
    public void resolveSnapshot()
            throws Exception
    {
        MavenCoordinates binarySpec = parseBinarySpec("food.fruit:banana:2.0-SNAPSHOT");
        Assert.assertEquals(
                repo.resolve(binarySpec),
                createBinarySpec("food.fruit", "banana", "2.0-SNAPSHOT", null, null, "2.0-20110311.201909-1"));

        URI uri = repo.getUri(binarySpec);
        Assert.assertTrue(uri.getPath().endsWith("/food/fruit/banana/2.0-SNAPSHOT/banana-2.0-20110311.201909-1.tar.gz"));
    }

    @Test
    public void resolveSnapshotWithDefaultGroupId()
            throws Exception
    {
        MavenCoordinates binarySpec = parseBinarySpec("banana:2.0-SNAPSHOT");
        Assert.assertEquals(
                repo.resolve(binarySpec),
                createBinarySpec("food.fruit", "banana", "2.0-SNAPSHOT", null, null, "2.0-20110311.201909-1"));

        URI uri = repo.getUri(binarySpec);
        Assert.assertTrue(uri.getPath().endsWith("/food/fruit/banana/2.0-SNAPSHOT/banana-2.0-20110311.201909-1.tar.gz"));
    }

    @Test
    public void resolveTimestamp()
            throws Exception
    {
        MavenCoordinates binarySpec = parseBinarySpec("food.fruit:banana:2.0-20110311.201909-1");
        Assert.assertEquals(
                repo.resolve(binarySpec),
                createBinarySpec("food.fruit", "banana", "2.0-SNAPSHOT", null, null, "2.0-20110311.201909-1"));

        URI uri = repo.getUri(binarySpec);
        Assert.assertTrue(uri.getPath().endsWith("/food/fruit/banana/2.0-SNAPSHOT/banana-2.0-20110311.201909-1.tar.gz"));
    }
}
