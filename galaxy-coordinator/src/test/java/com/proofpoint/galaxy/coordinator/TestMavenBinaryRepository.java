package com.proofpoint.galaxy.coordinator;

import com.proofpoint.galaxy.shared.BinarySpec;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.net.URI;

public class TestMavenBinaryRepository
{
    // This repo extends the MavenBinaryRepository so we can use it for testing
    private TestingBinaryRepository repo;

    @BeforeMethod
    public void setUp()
            throws Exception
    {
        repo = new TestingBinaryRepository();
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
        BinarySpec binarySpec = BinarySpec.valueOf("food.fruit:apple:1.0");
        Assert.assertEquals(
                repo.resolveBinarySpec(binarySpec),
                new BinarySpec("food.fruit", "apple", "1.0", null, null, "1.0"));

        URI uri = repo.getBinaryUri(binarySpec);
        Assert.assertTrue(uri.getPath().endsWith("/food/fruit/apple/1.0/apple-1.0.tar.gz"));
    }

    @Test
    public void resolveDefaultGroupId()
            throws Exception
    {
        BinarySpec binarySpec = BinarySpec.valueOf("apple:1.0");
        Assert.assertEquals(
                repo.resolveBinarySpec(binarySpec),
                new BinarySpec("food.fruit", "apple", "1.0", null, null, "1.0"));

        URI uri = repo.getBinaryUri(binarySpec);
        Assert.assertTrue(uri.getPath().endsWith("/food/fruit/apple/1.0/apple-1.0.tar.gz"));
    }

    @Test
    public void resolveSnapshot()
            throws Exception
    {
        BinarySpec binarySpec = BinarySpec.valueOf("food.fruit:banana:2.0-SNAPSHOT");
        Assert.assertEquals(
                repo.resolveBinarySpec(binarySpec),
                new BinarySpec("food.fruit", "banana", "2.0-SNAPSHOT", null, null, "2.0-20110311.201909-1"));

        URI uri = repo.getBinaryUri(binarySpec);
        Assert.assertTrue(uri.getPath().endsWith("/food/fruit/banana/2.0-SNAPSHOT/banana-2.0-20110311.201909-1.tar.gz"));
    }

    @Test
    public void resolveSnapshotWithDefaultGroupId()
            throws Exception
    {
        BinarySpec binarySpec = BinarySpec.valueOf("banana:2.0-SNAPSHOT");
        Assert.assertEquals(
                repo.resolveBinarySpec(binarySpec),
                new BinarySpec("food.fruit", "banana", "2.0-SNAPSHOT", null, null, "2.0-20110311.201909-1"));

        URI uri = repo.getBinaryUri(binarySpec);
        Assert.assertTrue(uri.getPath().endsWith("/food/fruit/banana/2.0-SNAPSHOT/banana-2.0-20110311.201909-1.tar.gz"));
    }

    @Test
    public void resolveTimestamp()
            throws Exception
    {
        BinarySpec binarySpec = BinarySpec.valueOf("food.fruit:banana:2.0-20110311.201909-1");
        Assert.assertEquals(
                repo.resolveBinarySpec(binarySpec),
                new BinarySpec("food.fruit", "banana", "2.0-SNAPSHOT", null, null, "2.0-20110311.201909-1"));

        URI uri = repo.getBinaryUri(binarySpec);
        Assert.assertTrue(uri.getPath().endsWith("/food/fruit/banana/2.0-SNAPSHOT/banana-2.0-20110311.201909-1.tar.gz"));
    }
}
