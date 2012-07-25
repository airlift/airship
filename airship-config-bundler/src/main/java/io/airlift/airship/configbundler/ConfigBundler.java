package com.proofpoint.galaxy.configbundler;

import com.google.common.io.NullOutputStream;
import com.proofpoint.log.Logging;
import com.proofpoint.log.LoggingConfiguration;
import org.iq80.cli.Cli;
import org.iq80.cli.Help;

import java.io.IOException;
import java.io.PrintStream;
import java.util.concurrent.Callable;

import static org.iq80.cli.Cli.buildCli;

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
                Logging logging = new Logging();
                logging.initialize(new LoggingConfiguration());
            }
            else {
                System.setOut(new PrintStream(new NullOutputStream()));
                System.setErr(new PrintStream(new NullOutputStream()));

                Logging logging = new Logging();
                logging.initialize(new LoggingConfiguration());
                logging.disableConsole();
            }
        }
        finally {
            System.setOut(out);
            System.setErr(err);
        }
    }
}
