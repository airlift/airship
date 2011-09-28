package com.proofpoint.galaxy.coordinator;

import org.testng.annotations.Test;

import static com.google.common.collect.ImmutableList.of;
import static com.proofpoint.galaxy.coordinator.Strings.shortestUniquePrefix;
import static org.testng.Assert.assertEquals;

public class TestStrings
{
    @Test
    public void test()
    {
        assertEquals(shortestUniquePrefix(of("aa", "aaa")), 3);
        assertEquals(shortestUniquePrefix(of("a")), 1);
        assertEquals(shortestUniquePrefix(of("aaaaa")), 1);
        assertEquals(shortestUniquePrefix(of("a", "b", "c")), 1);
        assertEquals(shortestUniquePrefix(of("axxxxx", "b", "c")), 1);
        assertEquals(shortestUniquePrefix(of("ax", "bx", "cx")), 1);
        assertEquals(shortestUniquePrefix(of("ax", "ay", "cx")), 2);
        assertEquals(shortestUniquePrefix(of("axxx", "ayyyy", "cxxxx")), 2);
        assertEquals(shortestUniquePrefix(of("aaaax", "aaaay")), 5);
        assertEquals(shortestUniquePrefix(of("aaaaxx", "aaaayx", "ccc")), 5);

        assertEquals(shortestUniquePrefix(of("a1", "b2", "b3", "b4", "b5")), 2);
    }

    @Test(expectedExceptions = IllegalArgumentException.class, expectedExceptionsMessageRegExp = ".*unique.*")
    public void testDuplicateStrings()
    {
        shortestUniquePrefix(of("aaa", "aaa"));
    }

    @Test(expectedExceptions = IllegalArgumentException.class, expectedExceptionsMessageRegExp = ".*unique.*")
    public void testDuplicateStrings2()
    {
        shortestUniquePrefix(of("aaa", "aa", "aaa"));
    }

    @Test(expectedExceptions = IllegalArgumentException.class, expectedExceptionsMessageRegExp = ".*unique.*")
    public void testDuplicateStrings3()
    {
        shortestUniquePrefix(of("aa", "aaa", "aa", "aa"));
    }

}
