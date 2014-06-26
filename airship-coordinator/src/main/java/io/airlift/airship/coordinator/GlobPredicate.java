package io.airlift.airship.coordinator;

import java.util.regex.Pattern;

public class GlobPredicate
        extends RegexPredicate
{
    private final String glob;

    public GlobPredicate(String glob)
    {
        super(globToPattern(glob));
        this.glob = glob;
    }

    @Override
    public String toString()
    {
        return glob;
    }

    public static Pattern globToPattern(String glob)
    {
        glob = glob.trim();
        StringBuilder regex = new StringBuilder(glob.length() * 2);

        boolean escaped = false;
        int curlyDepth = 0;
        for (char currentChar : glob.toCharArray()) {
            switch (currentChar) {
                case '*':
                    if (escaped) {
                        regex.append("\\*");
                    }
                    else {
                        regex.append(".*");
                    }
                    escaped = false;
                    break;
                case '?':
                    if (escaped) {
                        regex.append("\\?");
                    }
                    else {
                        regex.append('.');
                    }
                    escaped = false;
                    break;
                case '.':
                case '(':
                case ')':
                case '+':
                case '|':
                case '^':
                case '$':
                    regex.append('\\');
                    regex.append(currentChar);
                    escaped = false;
                    break;
                case '\\':
                    if (escaped) {
                        regex.append("\\\\");
                        escaped = false;
                    }
                    else {
                        escaped = true;
                    }
                    break;
                case '{':
                    if (escaped) {
                        regex.append("\\{");
                    }
                    else {
                        regex.append('(');
                        curlyDepth++;
                    }
                    escaped = false;
                    break;
                case '}':
                    if (curlyDepth > 0 && !escaped) {
                        regex.append(')');
                        curlyDepth--;
                    }
                    else if (escaped) {
                        regex.append("\\}");
                    }
                    else {
                        regex.append("}");
                    }
                    escaped = false;
                    break;
                case ',':
                    if (curlyDepth > 0 && !escaped) {
                        regex.append('|');
                    }
                    else if (escaped) {
                        regex.append("\\,");
                    }
                    else {
                        regex.append(",");
                    }
                    break;
                default:
                    escaped = false;
                    regex.append(currentChar);
            }
        }
        return Pattern.compile(regex.toString());
    }
}
