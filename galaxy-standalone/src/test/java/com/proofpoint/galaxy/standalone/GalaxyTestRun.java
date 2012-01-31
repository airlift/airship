package com.proofpoint.galaxy.standalone;

import com.google.common.collect.ObjectArrays;

public class GalaxyTestRun
{
    private static final String[] LOCAL_GLOBAL_ARGS = {
            // "--debug",
            "--environment", "local"
    };

    private static final String[] REMOTE_GLOBAL_ARGS = {
            // "--debug",
            "--environment", "remote"
    };

    public static void main(String[] args)
            throws Exception
    {
        execute(new String[0], "environment", "provision-local", "local", "/tmp/local",
                "--repository", "https://oss.sonatype.org/content/repositories/releases/",
                "--repository", "https://oss.sonatype.org/content/repositories/snapshots/",
                "--repository", "http://localhost:64001/v1/config/test/",
                "--maven-default-group-id", "com.proofpoint.platform"
        );
        example(LOCAL_GLOBAL_ARGS);

//        execute(new String[0], "environment", "add", "remote", "http://localhost:64000");
//        example(galaxyParser, REMOTE_GLOBAL_ARGS);
    }

    private static void example(String[] globalArgs)
            throws Exception
    {
        execute(globalArgs, "show");
        execute(globalArgs, "reset-to-actual", "-b", "*");
        execute(globalArgs, "install", "sample-server:0.50", "@sample-server/general/1.0");
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
