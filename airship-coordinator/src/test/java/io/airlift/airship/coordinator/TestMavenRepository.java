package com.proofpoint.galaxy.coordinator;

import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.File;
import java.net.URI;

public class TestMavenRepository
{
    // This repo extends the MavenRepository so we can use it for testing
    private TestingMavenRepository repo;

    @BeforeMethod
    public void setUp()
            throws Exception
    {
        repo = new TestingMavenRepository();
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
        Assert.assertEquals(uri, new File(repo.getTargetRepo(), "food/fruit/apple/1.0/apple-1.0.tar.gz").toURI());
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
        Assert.assertEquals(uri, new File(repo.getTargetRepo(), "food/fruit/apple/1.0/apple-1.0.tar.gz").toURI());
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
        Assert.assertEquals(uri, new File(repo.getTargetRepo(), "food/fruit/banana/2.0-SNAPSHOT/banana-2.0-20110311.201909-1.tar.gz").toURI());
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
        Assert.assertEquals(uri, new File(repo.getTargetRepo(), "food/fruit/banana/2.0-SNAPSHOT/banana-2.0-20110311.201909-1.tar.gz").toURI());
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

    @Test
    public void configShortName()
            throws Exception
    {
        Assert.assertEquals(repo.configShortName("@apple:1.0"), "apple");
    }

    @Test
    public void resolve()
            throws Exception
    {
        Assert.assertEquals(repo.configResolve("@apple:1.0"), "@prod:apple:1.0");
        Assert.assertEquals(repo.binaryResolve("apple:1.0"), "food.fruit:apple:1.0");
    }

    @Test
    public void toURI()
            throws Exception
    {
        Assert.assertEquals(repo.configToHttpUri("@apple:1.0"), new File(repo.getTargetRepo(), "prod/apple/1.0/apple-1.0.config").toURI());
        Assert.assertEquals(repo.binaryToHttpUri("apple:1.0"), new File(repo.getTargetRepo(), "food/fruit/apple/1.0/apple-1.0.tar.gz").toURI());
    }

    @Test
    public void upgrade()
            throws Exception
    {
        Assert.assertEquals(repo.configUpgrade("@apple:1.0", "@2.0"), "@prod:apple:2.0");
        Assert.assertEquals(repo.binaryUpgrade("apple:1.0", "2.0"), "food.fruit:apple:2.0");

        Assert.assertEquals(repo.configUpgrade("@apple:1.0", "@banana:2.0-SNAPSHOT"), "@prod:banana:2.0-20110311.201909-1");
        Assert.assertEquals(repo.binaryUpgrade("apple:1.0", "banana:2.0-SNAPSHOT"), "food.fruit:banana:2.0-20110311.201909-1");
    }

    @Test
    public void equalsIgnoreVersion()
            throws Exception
    {
        Assert.assertTrue(repo.configEqualsIgnoreVersion("@apple:1.0", "@apple:2.0"));
        Assert.assertTrue(repo.binaryEqualsIgnoreVersion("apple:1.0", "apple:2.0"));

        Assert.assertFalse(repo.configEqualsIgnoreVersion("@apple:1.0", "@banana:1.0"));
        Assert.assertFalse(repo.binaryEqualsIgnoreVersion("apple:1.0", "banana:1.0"));
    }
}
