package io.airlift.airship.coordinator;

import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.File;
import java.util.regex.Pattern;

public class TestHttpRepository
{
    // This repo extends the HttpRepository so we can use it for testing
    private TestingHttpRepository repo;

    @BeforeMethod
    public void setUp()
            throws Exception
    {
        repo = new TestingHttpRepository();
    }

    @AfterMethod
    public void tearDown()
            throws Exception
    {
        repo.destroy();
    }

    @Test
    public void configShortName()
            throws Exception
    {
        Assert.assertEquals(repo.configShortName("@apple-1.0.config"), "apple");
    }

    @Test
    public void resolve()
            throws Exception
    {
        Assert.assertEquals(repo.configResolve("@apple-1.0.config"), "@apple-1.0.config");
        Assert.assertEquals(repo.binaryResolve("apple-1.0.config"), "apple-1.0.config");

        Assert.assertEquals(repo.configResolve("@" + new File(repo.getTargetRepo(), "apple-1.0.config").toURI().toASCIIString()),
                "@" + new File(repo.getTargetRepo(), "apple-1.0.config").toURI().toASCIIString());
        Assert.assertEquals(repo.binaryResolve(new File(repo.getTargetRepo(), "apple-1.0.config").toURI().toASCIIString()),
                new File(repo.getTargetRepo(), "apple-1.0.config").toURI().toASCIIString());
    }

    @Test
    public void toURI()
            throws Exception
    {
        Assert.assertEquals(repo.configToHttpUri("@apple-1.0.config"), new File(repo.getTargetRepo(), "apple-1.0.config").toURI());
        Assert.assertEquals(repo.binaryToHttpUri("apple-1.0.config"), new File(repo.getTargetRepo(), "apple-1.0.config").toURI());

        Assert.assertEquals(repo.configToHttpUri("@" + new File(repo.getTargetRepo(), "apple-1.0.config").toURI().toASCIIString()),
                new File(repo.getTargetRepo(), "apple-1.0.config").toURI());
        Assert.assertEquals(repo.binaryToHttpUri(new File(repo.getTargetRepo(), "apple-1.0.config").toURI().toASCIIString()),
                new File(repo.getTargetRepo(), "apple-1.0.config").toURI());
    }

    @Test
    public void upgrade()
            throws Exception
    {
        Assert.assertEquals(repo.configUpgrade("@apple-1.0.tar.gz", "@2.0"), "@apple-2.0.tar.gz");
        Assert.assertEquals(repo.binaryUpgrade("apple-1.0.tar.gz", "2.0"), "apple-2.0.tar.gz");

        Assert.assertEquals(repo.configUpgrade("@apple-1.0.tar.gz", "@banana-2.0-SNAPSHOT.tar.gz"), "@banana-2.0-SNAPSHOT.tar.gz");
        Assert.assertEquals(repo.binaryUpgrade("apple-1.0.tar.gz", "banana-2.0-SNAPSHOT.tar.gz"), "banana-2.0-SNAPSHOT.tar.gz");

        Assert.assertEquals(repo.configUpgrade("@" + new File(repo.getTargetRepo(), "apple-1.0.tar.gz").toURI().toASCIIString(), "@2.0"),
                "@" + new File(repo.getTargetRepo(), "apple-2.0.tar.gz").toURI().toASCIIString());
        Assert.assertEquals(repo.binaryUpgrade(new File(repo.getTargetRepo(), "apple-1.0.tar.gz").toURI().toASCIIString(), "2.0"),
                new File(repo.getTargetRepo(), "apple-2.0.tar.gz").toURI().toASCIIString());
    }

    @Test
    public void equalsIgnoreVersion()
            throws Exception
    {
        Assert.assertTrue(repo.configEqualsIgnoreVersion("@apple-1.0.tar.gz", "@apple-2.0.tar.gz"));
        Assert.assertTrue(repo.binaryEqualsIgnoreVersion("apple-1.0.tar.gz", "apple-2.0.tar.gz"));

        Assert.assertFalse(repo.configEqualsIgnoreVersion("@apple-1.0.tar.gz", "@banana-1.0.tar.gz"));
        Assert.assertFalse(repo.binaryEqualsIgnoreVersion("apple-1.0.tar.gz", "banana-1.0.tar.gz"));
    }

    @Test
    public void upgradeVersion()
    {
        Assert.assertEquals(HttpRepository.upgradePath("#", "X", Pattern.compile("(#)")), "X");
        Assert.assertEquals(HttpRepository.upgradePath("##", "X", Pattern.compile("(#)")), "XX");
        Assert.assertEquals(HttpRepository.upgradePath("a#b", "X", Pattern.compile("(#)")), "aXb");
        Assert.assertEquals(HttpRepository.upgradePath("a#b#c#d", "X", Pattern.compile("(#)")), "aXbXcXd");

        Assert.assertEquals(HttpRepository.upgradePath("#ooo@", "X", Pattern.compile("(#)ooo(@)")), "XoooX");
        Assert.assertEquals(HttpRepository.upgradePath("#ooo@#ooo@", "X", Pattern.compile("(#)ooo(@)")), "XoooXXoooX");
        Assert.assertEquals(HttpRepository.upgradePath("a#ooo@b", "X", Pattern.compile("(#)ooo(@)")), "aXoooXb");
        Assert.assertEquals(HttpRepository.upgradePath("a#ooo@b#ooo@c", "X", Pattern.compile("(#)ooo(@)")), "aXoooXbXoooXc");
    }
}
