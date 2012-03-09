package com.proofpoint.galaxy.shared;

import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Sets;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.SortedSet;

import static com.google.common.collect.Collections2.transform;
import static com.google.common.collect.Lists.newArrayList;
import static java.lang.Math.max;
import static java.lang.Math.min;

public class Strings
{
    public static int commonPrefixSegments(char separator, Collection<String> strings)
    {
        return commonPrefixSegments(separator, strings, 0);
    }

    public static int commonPrefixSegments(char separator, Collection<String> strings, int minSize)
    {
        Preconditions.checkNotNull(strings, "strings is null");
        Preconditions.checkArgument(minSize >= 0, "minSize is negative");

        if (strings.isEmpty()) {
            return 0;
        }

        int shortestNumberOfParts = Integer.MAX_VALUE;
        List<List<String>> stringsParts = newArrayList();
        for (String string : strings) {
            List<String> parts = ImmutableList.copyOf(Splitter.on(separator).split(string));
            if (parts.isEmpty() || !parts.get(0).isEmpty()) {
                throw new IllegalArgumentException("All strings must start with the separator character");
            }
            parts = parts.subList(1, parts.size());
            stringsParts.add(parts);
            shortestNumberOfParts = min(parts.size(), shortestNumberOfParts);
        }

        int maxNumberOfSharedParts = max(shortestNumberOfParts - minSize, 0);

        int commonParts = 0;
        while (commonParts < maxNumberOfSharedParts && isPartEqual(commonParts, stringsParts)) {
            commonParts++;
        }

        return commonParts;
    }

    private static boolean isPartEqual(int partNumber, List<List<String>> stringsParts)
    {
        if (stringsParts.get(0).size() <= partNumber) {
            return false;
        }
        String part = stringsParts.get(0).get(partNumber);
        for (List<String> parts : stringsParts) {
            if (parts.size() <= partNumber || !part.equals(parts.get(partNumber))) {
                return false;
            }
        }
        return true;
    }

    public static String trimLeadingSegments(String string, char separator, int segmentCount)
    {
        if (string == null) {
            return null;
        }

        List<String> segments = ImmutableList.copyOf(Splitter.on(separator).split(string));
        if (segments.isEmpty() || !segments.get(0).isEmpty()) {
            throw new IllegalArgumentException("String must start with the separator character");
        }
        segments = segments.subList(1, segments.size());

        if (segments.size() < segmentCount) {
            return string;
        }
        String trimmedString = Joiner.on(separator).join(segments.subList(segmentCount, segments.size()));
        if (!trimmedString.startsWith("" + separator)) {
            trimmedString = separator + trimmedString;
        }
        return trimmedString;
    }

    public static int shortestUniquePrefix(Collection<String> strings)
    {
        return shortestUniquePrefix(strings, 1);
    }

    public static int shortestUniquePrefix(Collection<String> strings, int minSize)
    {
        Preconditions.checkNotNull(strings, "strings is null");
        if (strings.size() < 2) {
            return minSize;
        }

        SortedSet<String> sorted = Sets.newTreeSet(strings);
        if (sorted.size() != strings.size()) {
            throw new IllegalArgumentException("Cannot compute unique prefix size for collection with duplicate entries");
        }

        int prefix = shortestUniquePrefixStartingAt(ImmutableList.copyOf(sorted), 0);
        return max(prefix, minSize);
    }

    private static int shortestUniquePrefixStartingAt(List<String> strings, int charPosition)
    {
        Preconditions.checkArgument(!strings.isEmpty(), "strings is empty");
        Preconditions.checkArgument(charPosition < Collections.max(transform(strings, lengthGetter())),
                "charPosition is beyond the size of all the provided strings");

        int result = 1;

        // assumes sorted list
        // the algorithm goes like this:
        //   1. identify sequences of strings that start with the same character.
        //      Strings are sorted, so it's just a matter of scanning until the char changes
        //   2. recursively, compute the unique prefix of these strings, starting at the next character position
        //   3. the shortest unique prefix is the max between all the sequences + 1
        int candidates = 0;
        boolean first = true;
        char commonChar = 0;
        int sequenceStart = 0;
        int index = -1;

        for (String value : strings) {
            ++index;
            if (charPosition >= value.length()) {
                continue;
            }

            candidates++;

            char currentChar = value.charAt(charPosition);
            if (first) {
                commonChar = currentChar;
                first = false;
                continue;
            }

            if (currentChar != commonChar) {
                if (index - sequenceStart > 1) {
                    // only recurse if we have more than one item to process in the sequence
                    result = max(result, shortestUniquePrefixStartingAt(strings.subList(sequenceStart, index), charPosition + 1) + 1);
                }

                sequenceStart = index;
                commonChar = currentChar;
            }
        }

        // deal with the last sequence
        if (candidates > 1 && strings.size() - sequenceStart > 1) {
            result = max(result, shortestUniquePrefixStartingAt(strings.subList(sequenceStart, strings.size()), charPosition + 1) + 1);
        }

        return result;
    }

    public static String safeTruncate(String string, int length)
    {
        if (string == null) {
            return null;
        }
        if (length > string.length()) {
            return string;
        }
        return string.substring(0, length);
    }

    private static Function<String, Integer> lengthGetter()
    {
        return new Function<String, Integer>()
        {
            public Integer apply(String input)
            {
                return input.length();
            }
        };
    }
}
