package com.proofpoint.galaxy.configbundler;

import org.iq80.cli.Cli;
import org.iq80.cli.Help;

import java.util.concurrent.Callable;

import static org.iq80.cli.Cli.buildCli;

public class ConfigBundler
{
    public static void main(String[] args)
            throws Exception
    {
        Cli<Callable> cli = buildCli("configgy", Callable.class)
                .withDefaultCommand(Help.class)
                .withCommands(ReleaseCommand.class)
                .withCommand(Help.class)
                .build();

        cli.parse(args).call();
    }

}
