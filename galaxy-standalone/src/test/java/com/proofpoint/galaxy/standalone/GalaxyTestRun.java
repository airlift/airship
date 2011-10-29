package com.proofpoint.galaxy.standalone;

import com.google.common.collect.ObjectArrays;

public class GalaxyTestRun
{
    private static final String[] LOCAL_GLOBAL_ARGS = {
            "--environment", "test",
            "--coordinator", "/tmp/agent/slots",
            "--binary-repository", "https://oss.sonatype.org/content/repositories/releases/",
            "--binary-repository", "https://oss.sonatype.org/content/repositories/snapshots/",
            "--config-repository", "http://localhost:64001/v1/config/"
    };

    private static final String[] REMOTE_GLOBAL_ARGS = {
    };

    public static void main(String[] args)
            throws Exception
    {
//        example(galaxyParser, LOCAL_GLOBAL_ARGS);
        example(LOCAL_GLOBAL_ARGS);
    }

    private static void example(String[] globalArgs)
            throws Exception
    {
        execute(globalArgs, "show");
        execute(globalArgs, "reset-to-actual", "-b", "*");
        execute(globalArgs, "install", "com.proofpoint.platform:sample-server:0.50", "@sample-server:general:1.0");
        Thread.sleep(2000);
        execute(globalArgs, "show");
        execute(globalArgs, "start", "-b", "*");
        Thread.sleep(2000);
        execute(globalArgs, "show");
        execute(globalArgs, "upgrade", "-b", "*", "0.51");
        Thread.sleep(2000);
        execute(globalArgs, "show", "-b", "*");
        execute(globalArgs, "restart", "-b", "*");
        Thread.sleep(2000);
        execute(globalArgs, "show", "-b", "*");
        execute(globalArgs, "stop", "-b", "*");
        Thread.sleep(2000);
        execute(globalArgs, "show", "-b", "*");
        execute(globalArgs, "terminate", "-b", "*");
        Thread.sleep(2000);
        execute(globalArgs, "show", "-b", "*");
    }

    public static void execute(String[] defaultGlobalArgs, String... args)
            throws Exception
    {
        System.out.println(args[0]);
        Galaxy.main(ObjectArrays.concat(defaultGlobalArgs, args, String.class));
        System.out.println();
        System.out.println();
    }
}
