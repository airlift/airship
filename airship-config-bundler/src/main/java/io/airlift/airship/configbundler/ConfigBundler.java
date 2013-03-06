package io.airlift.airship.configbundler;

import com.google.common.io.NullOutputStream;
import io.airlift.command.Cli;
import io.airlift.command.Help;
import io.airlift.log.Logging;
import io.airlift.log.LoggingConfiguration;

import java.io.IOException;
import java.io.PrintStream;
import java.util.concurrent.Callable;

import static io.airlift.command.Cli.buildCli;

public class ConfigBundler
{
    public static void main(String[] args)
            throws Exception
    {
        initializeLogging(false);

        Cli<Callable> cli = buildCli("configgy", Callable.class)
                .withDefaultCommand(Help.class)
                .withCommands(ReleaseCommand.class,
                        InitCommand.class,
                        AddComponentCommand.class,
                        SnapshotCommand.class)
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
                System.setOut(new PrintStream(new NullOutputStream()));
                System.setErr(new PrintStream(new NullOutputStream()));

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
