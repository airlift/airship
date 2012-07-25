package io.airlift.airship.shared;

import com.google.common.collect.ImmutableList;
import org.testng.annotations.Test;

import static com.google.common.collect.ImmutableList.of;
import static io.airlift.airship.shared.Strings.commonPrefixSegments;
import static io.airlift.airship.shared.Strings.shortestUniquePrefix;
import static io.airlift.airship.shared.Strings.trimLeadingSegments;
import static org.testng.Assert.assertEquals;

public class TestStrings
{
    @Test
    public void testCommonSegment()
    {
        assertEquals(commonPrefixSegments('/', ImmutableList.<String>of()), 0);
        assertEquals(commonPrefixSegments('/', of("/")), 1);
        assertEquals(commonPrefixSegments('/', of("/a")), 1);
        assertEquals(commonPrefixSegments('/', of("/a", "/a")), 1);
        assertEquals(commonPrefixSegments('/', of("/a/a/a", "/a/a/a")), 3);
        assertEquals(commonPrefixSegments('/', of("/a/a/a", "/a/a/b")), 2);
        assertEquals(commonPrefixSegments('/', of("/a/a/a", "/a/a/a", "/a/a/b")), 2);
    }

    @Test
    public void testCommonSegmentWithMin()
    {
        assertEquals(commonPrefixSegments('/', ImmutableList.<String>of(), 2), 0);

        assertEquals(commonPrefixSegments('/', of("/"), 0), 1);
        assertEquals(commonPrefixSegments('/', of("/"), 1), 0);
        assertEquals(commonPrefixSegments('/', of("/"), 2), 0);

        assertEquals(commonPrefixSegments('/', of("/a"), 0), 1);
        assertEquals(commonPrefixSegments('/', of("/a"), 1), 0);
        assertEquals(commonPrefixSegments('/', of("/a"), 2), 0);

        assertEquals(commonPrefixSegments('/', of("/a", "/a"), 0), 1);
        assertEquals(commonPrefixSegments('/', of("/a", "/a"), 1), 0);
        assertEquals(commonPrefixSegments('/', of("/a", "/a"), 2), 0);

        assertEquals(commonPrefixSegments('/', of("/a/a/a", "/a/a/a"), 0), 3);
        assertEquals(commonPrefixSegments('/', of("/a/a/a", "/a/a/a"), 1), 2);
        assertEquals(commonPrefixSegments('/', of("/a/a/a", "/a/a/a"), 2), 1);
        assertEquals(commonPrefixSegments('/', of("/a/a/a", "/a/a/a"), 3), 0);
        assertEquals(commonPrefixSegments('/', of("/a/a/a", "/a/a/a"), 4), 0);

        assertEquals(commonPrefixSegments('/', of("/a/a/a", "/a/a/b"), 0), 2);
        assertEquals(commonPrefixSegments('/', of("/a/a/a", "/a/a/b"), 1), 2);
        assertEquals(commonPrefixSegments('/', of("/a/a/a", "/a/a/b"), 2), 1);
        assertEquals(commonPrefixSegments('/', of("/a/a/a", "/a/a/b"), 3), 0);
        assertEquals(commonPrefixSegments('/', of("/a/a/a", "/a/a/b"), 4), 0);

        assertEquals(commonPrefixSegments('/', of("/a/a/a", "/a/a/a", "/a/a/b"), 0), 2);
        assertEquals(commonPrefixSegments('/', of("/a/a/a", "/a/a/a", "/a/a/b"), 1), 2);
        assertEquals(commonPrefixSegments('/', of("/a/a/a", "/a/a/a", "/a/a/b"), 2), 1);
        assertEquals(commonPrefixSegments('/', of("/a/a/a", "/a/a/a", "/a/a/b"), 3), 0);
        assertEquals(commonPrefixSegments('/', of("/a/a/a", "/a/a/a", "/a/a/b"), 4), 0);
    }

    @Test
    public void testTrim() {
        assertEquals(trimLeadingSegments("/a/b/c", '/', 0), "/a/b/c");
        assertEquals(trimLeadingSegments("/a/b/c", '/', 1), "/b/c");
        assertEquals(trimLeadingSegments("/a/b/c", '/', 2), "/c");
    }

    @Test
    public void testShortestUniquePrefix()
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
