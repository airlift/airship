package com.proofpoint.galaxy.coordinator;

import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.net.URI;

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
        String binarySpec = "food.fruit:apple:1.0";
        Assert.assertEquals(
                repo.binaryResolve(binarySpec),
                "food.fruit:apple:1.0");

        URI uri = repo.binaryToHttpUri(binarySpec);
        Assert.assertTrue(uri.getPath().endsWith("/food/fruit/apple/1.0/apple-1.0.tar.gz"));
    }

    @Test
    public void resolveDefaultGroupId()
            throws Exception
    {
        String binarySpec = "apple:1.0";
        Assert.assertEquals(
                repo.binaryResolve(binarySpec),
                "food.fruit:apple:1.0");

        URI uri = repo.binaryToHttpUri(binarySpec);
        Assert.assertTrue(uri.getPath().endsWith("/food/fruit/apple/1.0/apple-1.0.tar.gz"));
    }

    @Test
    public void resolveSnapshot()
            throws Exception
    {
        String binarySpec = "food.fruit:banana:2.0-SNAPSHOT";
        Assert.assertEquals(
                repo.binaryResolve(binarySpec),
                "food.fruit:banana:2.0-20110311.201909-1");

        URI uri = repo.binaryToHttpUri(binarySpec);
        Assert.assertTrue(uri.getPath().endsWith("/food/fruit/banana/2.0-SNAPSHOT/banana-2.0-20110311.201909-1.tar.gz"));
    }

    @Test
    public void resolveSnapshotWithDefaultGroupId()
            throws Exception
    {
        String binarySpec = "banana:2.0-SNAPSHOT";
        Assert.assertEquals(
                repo.binaryResolve(binarySpec),
                "food.fruit:banana:2.0-20110311.201909-1");

        URI uri = repo.binaryToHttpUri(binarySpec);
        Assert.assertTrue(uri.getPath().endsWith("/food/fruit/banana/2.0-SNAPSHOT/banana-2.0-20110311.201909-1.tar.gz"));
    }

    @Test
    public void resolveTimestamp()
            throws Exception
    {
        String binarySpec = "food.fruit:banana:2.0-20110311.201909-1";
        Assert.assertEquals(
                repo.binaryResolve(binarySpec),
                "food.fruit:banana:2.0-20110311.201909-1");

        URI uri = repo.binaryToHttpUri(binarySpec);
        Assert.assertTrue(uri.getPath().endsWith("/food/fruit/banana/2.0-SNAPSHOT/banana-2.0-20110311.201909-1.tar.gz"));
    }
}
