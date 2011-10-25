package com.proofpoint.galaxy.coordinator;

import com.google.common.base.Function;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Sets;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.SortedSet;

import static com.google.common.collect.Collections2.transform;
import static java.lang.Math.max;

public class Strings
{
    public static int shortestUniquePrefix(Collection<String> strings)
    {
        Preconditions.checkNotNull(strings, "strings is null");
        if (strings.isEmpty()) {
            return 0;
        }

        SortedSet<String> sorted = Sets.newTreeSet(strings);
        if (sorted.size() != strings.size()) {
            throw new IllegalArgumentException("Cannot compute unique prefix size for collection with duplicate entries");
        }

        return shortestUniquePrefix(ImmutableList.copyOf(sorted), 0);
    }

    private static int shortestUniquePrefix(List<String> strings, int charPosition)
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
                    result = max(result, shortestUniquePrefix(strings.subList(sequenceStart, index), charPosition + 1) + 1);
                }

                sequenceStart = index;
                commonChar = currentChar;
            }
        }

        // deal with the last sequence
        if (candidates > 1 && strings.size() - sequenceStart > 1) {
            result = max(result, shortestUniquePrefix(strings.subList(sequenceStart, strings.size()), charPosition + 1) + 1);
        }

        return result;
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
