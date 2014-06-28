package io.airlift.airship.configbundler;

import io.airlift.airline.Cli;
import io.airlift.airline.Help;
import io.airlift.log.Logging;
import io.airlift.log.LoggingConfiguration;

import java.io.IOException;
import java.io.PrintStream;
import java.util.concurrent.Callable;

import static com.google.common.io.ByteStreams.nullOutputStream;

public class ConfigBundler
{
    public static void main(String[] args)
            throws Exception
    {
        initializeLogging(false);

        Cli<Callable<Void>> cli = Cli.<Callable<Void>>builder("asconfig")
                .withDefaultCommand(Help.class)
                .withCommand(ReleaseCommand.class)
                .withCommand(InitCommand.class)
                .withCommand(AddComponentCommand.class)
                .withCommand(SnapshotCommand.class)
                .withCommand(InfoCommand.class)
                .withCommand(Help.class)
                .build();

        cli.parse(args).call();
    }

    public static void initializeLogging(boolean debug)
            throws IOException
    {
        // unhook out and err while initializing logging or logger will print to them
        PrintStream out = System.out;
        PrintStream err = System.err;
        try {
            if (debug) {
                Logging logging = Logging.initialize();
                logging.configure(new LoggingConfiguration());
            }
            else {
                System.setOut(new PrintStream(nullOutputStream()));
                System.setErr(new PrintStream(nullOutputStream()));

                Logging logging = Logging.initialize();
                logging.configure(new LoggingConfiguration());
                logging.disableConsole();
            }
        }
        finally {
            System.setOut(out);
            System.setErr(err);
        }
    }
}
