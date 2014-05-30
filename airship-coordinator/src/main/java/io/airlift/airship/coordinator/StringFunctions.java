package io.airlift.airship.coordinator;

import com.google.common.base.Function;
import com.google.common.base.Predicate;

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
