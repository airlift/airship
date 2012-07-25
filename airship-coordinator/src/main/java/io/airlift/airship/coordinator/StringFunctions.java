package com.proofpoint.galaxy.coordinator;

import com.google.common.base.Function;
import com.google.common.base.Predicate;

import javax.annotation.Nullable;
import java.util.regex.Pattern;

import static com.google.common.base.Predicates.contains;
import static com.google.common.base.Predicates.not;

public class StringFunctions
{
    public static Function<String, String> toLowerCase()
    {
        return new Function<String, String>()
        {
            public String apply(String input)
            {
                return input.toLowerCase();
            }
        };
    }

    public static <T> Function<T, String> toStringFunction()
    {
        return new Function<T, String>()
        {
            public String apply(T input)
            {
                return input.toString();
            }
        };
    }

    public static Predicate<String> startsWith(final String prefix)
    {
        return new Predicate<String>()
        {
            public boolean apply(String input)
            {
                return input.startsWith(prefix);
            }
        };
    }
}
