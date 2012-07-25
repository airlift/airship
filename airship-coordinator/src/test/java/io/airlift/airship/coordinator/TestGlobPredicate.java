package io.airlift.airship.coordinator;

import org.testng.annotations.Test;

import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

public class TestGlobPredicate
{
    @Test
    public void testGlobPredicate()
    {
        GlobPredicate globPredicate = new GlobPredicate("*");
        assertTrue(globPredicate.apply("text"));
        assertTrue(globPredicate.apply(""));
        assertFalse(globPredicate.apply(null));

        globPredicate = new GlobPredicate("a*b*");
        assertTrue(globPredicate.apply("aXbX"));
        assertFalse(globPredicate.apply(null));
        assertFalse(globPredicate.apply("x"));
        assertFalse(globPredicate.apply("xab"));

        globPredicate = new GlobPredicate("*.txt");
        assertTrue(globPredicate.apply("readme.txt"));
        assertTrue(globPredicate.apply(".txt"));
        assertTrue(globPredicate.apply(" .txt"));
        assertFalse(globPredicate.apply(null));
        assertFalse(globPredicate.apply("txt"));
        assertFalse(globPredicate.apply("readme.txts"));

        globPredicate = new GlobPredicate("[abc].txt");
        assertTrue(globPredicate.apply("a.txt"));
        assertTrue(globPredicate.apply("b.txt"));
        assertTrue(globPredicate.apply("c.txt"));
        assertFalse(globPredicate.apply(null));
        assertFalse(globPredicate.apply("x.txt"));
        assertFalse(globPredicate.apply("a.tt"));
        assertFalse(globPredicate.apply("aa.txt"));
        assertFalse(globPredicate.apply(" .txt"));

        globPredicate = new GlobPredicate("*.{txt,html}");
        assertTrue(globPredicate.apply("readme.txt"));
        assertTrue(globPredicate.apply("readme.html"));
        assertTrue(globPredicate.apply(".txt"));
        assertTrue(globPredicate.apply(".html"));
        assertTrue(globPredicate.apply(" .txt"));
        assertTrue(globPredicate.apply(" .html"));
        assertFalse(globPredicate.apply(null));
        assertFalse(globPredicate.apply("txt"));
        assertFalse(globPredicate.apply("html"));
        assertFalse(globPredicate.apply("*.{txt,html}"));
        assertFalse(globPredicate.apply("readme.txthtml"));

    }
}
