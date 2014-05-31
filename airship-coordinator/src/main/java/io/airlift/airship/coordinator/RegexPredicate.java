package io.airlift.airship.coordinator;

import com.google.common.base.Predicate;

import javax.annotation.Nullable;

import java.util.regex.Pattern;

public class RegexPredicate implements Predicate<CharSequence>
{
    private final Pattern pattern;

    public RegexPredicate(String regex)
    {
        this(Pattern.compile(regex));
    }

    public RegexPredicate(Pattern pattern)
    {
        this.pattern = pattern;
    }

    public boolean apply(@Nullable CharSequence input)
    {
        return input != null && pattern.matcher(input).matches();
    }

    @Override
    public String toString()
    {
        return pattern.pattern();
    }
}
