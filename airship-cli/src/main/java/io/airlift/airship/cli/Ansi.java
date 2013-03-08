package io.airlift.airship.cli;

import org.fusesource.jansi.Ansi.Color;

import static org.fusesource.jansi.Ansi.ansi;
import static org.fusesource.jansi.internal.CLibrary.STDOUT_FILENO;
import static org.fusesource.jansi.internal.CLibrary.isatty;

public class Ansi
{
    private static final boolean IS_A_TTY = (isatty(STDOUT_FILENO) != 0);

    public static boolean isEnabled()
    {
        return IS_A_TTY;
    }

    public static String colorize(Object value, Color color)
    {
        if (value == null) {
            return "";
        }
        return ansi().fg(color).a(value).reset().toString();
    }
}
