package com.proofpoint.galaxy.standalone;

import jnr.posix.POSIX;
import org.fusesource.jansi.Ansi.Color;

import java.io.FileDescriptor;

import static org.fusesource.jansi.Ansi.ansi;

public class Ansi
{
    private static final boolean IS_A_TTY;

    static {
        POSIX posix = POSIXFactory.getPOSIX();
        IS_A_TTY = posix.isatty(FileDescriptor.out);
    }

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
