package io.airlift.airship.cli;

import com.google.common.base.Joiner;

public class AirshipTestRun
{
    public static void main(String[] args)
            throws Exception
    {
//        airship environment provision-aws prod \
//                --repository https://oss.sonatype.org/content/repositories/releases/ \
//                --repository https://oss.sonatype.org/content/repositories/snapshots/ \
//                --repository https://s3.amazonaws.com/airship-cloud-formation/ \
//                --key-pair dain \
//                --coordinator-config @coordinator.config

//        execute("environment", "provision-aws", "prod",
//                "--repository", "https://oss.sonatype.org/content/repositories/releases/",
//                "--repository", "https://oss.sonatype.org/content/repositories/snapshots/",
//                "--repository", "https://s3.amazonaws.com/airship-cloud-formation/",
//                "--maven-default-group-id", "io.airlift",
//                "--key-pair", "dain",
//                "--coordinator-config", "@coordinator.config"
//                );

        execute("environment", "provision-local", "local", "/tmp/local",
                "--name", "monkey",
                "--repository", "https://oss.sonatype.org/content/repositories/releases/",
                "--repository", "https://oss.sonatype.org/content/repositories/snapshots/",
                "--repository", "http://localhost:64001/v1/config/test/",
                "--maven-default-group-id", "io.airlift"
        );
        execute("environment", "use", "local");

        example();

    }

    private static void example()
            throws Exception
    {
        execute("show");
        execute("reset-to-actual", "-b", "*");
        execute("install", "sample-server:0.50", "@sample-server/general/1.0");
        Thread.sleep(2000);
        execute("show");
        execute("start", "-b", "*");
        Thread.sleep(2000);
        execute("show");
        execute("upgrade", "-b", "*", "0.51");
        Thread.sleep(2000);
        execute("show", "-b", "*");
        execute("restart", "-b", "*");
        Thread.sleep(2000);
        execute("show", "-b", "*");
        execute("stop", "-b", "*");
        Thread.sleep(2000);
        execute("show", "-b", "*");
        execute("terminate", "-b", "*");
        Thread.sleep(2000);
        execute("show", "-b", "*");
    }

    public static void execute(String... args)
            throws Exception
    {
        System.out.println(Joiner.on(" ").join(args));
        Airship.main(args);
        System.out.println();
        System.out.println();
    }
}
