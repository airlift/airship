package com.proofpoint.galaxy.coordinator;

import org.testng.annotations.Test;

import java.util.regex.Pattern;

import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

public class TestRegexPredicate
{
    @Test
    public void testRegexPredicate()
    {
        RegexPredicate matchAllPredicate = new RegexPredicate(Pattern.compile(".*"));
        assertTrue(matchAllPredicate.apply("text"));
        assertTrue(matchAllPredicate.apply(""));
        assertFalse(matchAllPredicate.apply(null));

        matchAllPredicate = new RegexPredicate(".*");
        assertTrue(matchAllPredicate.apply("text"));
        assertTrue(matchAllPredicate.apply(""));
        assertFalse(matchAllPredicate.apply(null));

        RegexPredicate regexPredicate = new RegexPredicate("a+b*");
        assertTrue(regexPredicate.apply("a"));
        assertTrue(regexPredicate.apply("ab"));
        assertFalse(regexPredicate.apply(null));
        assertFalse(regexPredicate.apply("x"));
        assertFalse(regexPredicate.apply("xab"));
        assertFalse(regexPredicate.apply("abX"));
        assertFalse(regexPredicate.apply("XabX"));
    }
}
