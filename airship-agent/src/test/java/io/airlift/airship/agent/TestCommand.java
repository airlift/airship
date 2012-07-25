package io.airlift.airship.agent;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import io.airlift.airship.shared.Command;
import io.airlift.airship.shared.CommandFailedException;
import io.airlift.airship.shared.CommandTimeoutException;
import com.proofpoint.testing.EquivalenceTester;
import com.proofpoint.units.Duration;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static java.util.Arrays.asList;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotSame;

public class TestCommand
{
    private ExecutorService executor;

    @BeforeClass
    public void setUp()
            throws Exception
    {
        executor = Executors.newCachedThreadPool(new ThreadFactoryBuilder().setDaemon(true).setNameFormat("process-input-reader-%s").build());
    }

    @AfterClass
    public void tearDown()
            throws Exception
    {
        executor.shutdownNow();
    }

    @Test
    public void buildCommandChainNewObjects()
            throws Exception
    {
        Command command = new Command("foo");
        assertNotSame(command.setDirectory("foo"), command);
        assertNotSame(command.setDirectory(new File("foo")), command);
        assertNotSame(command.setSuccessfulExitCodes(42), command);
        assertNotSame(command.setSuccessfulExitCodes(ImmutableSet.of(42)), command);
        assertNotSame(command.setTimeLimit(2, TimeUnit.SECONDS), command);
        assertNotSame(command.setTimeLimit(new Duration(2, TimeUnit.SECONDS)), command);
    }

    @Test
    public void buildCommand()
            throws Exception
    {
        Command expected = new Command("a", "b", "c")
                .setDirectory("directory")
                .setSuccessfulExitCodes(33, 44)
                .setTimeLimit(5, TimeUnit.SECONDS);

        Command actual = new Command("a", "b", "c")
                .setDirectory("directory")
                .setSuccessfulExitCodes(33, 44)
                .setTimeLimit(5, TimeUnit.SECONDS);
        assertEquals(actual, expected);

        // call every setter and make sure the actual, command never changes
        assertNotSame(actual.setDirectory("foo"), actual);
        assertEquals(actual, expected);

        assertNotSame(actual.setDirectory(new File("foo")), actual);
        assertEquals(actual, expected);

        assertNotSame(actual.setSuccessfulExitCodes(42), actual);
        assertEquals(actual, expected);

        assertNotSame(actual.setSuccessfulExitCodes(ImmutableSet.of(42)), actual);
        assertEquals(actual, expected);

        assertNotSame(actual.setTimeLimit(2, TimeUnit.SECONDS), actual);
        assertEquals(actual, expected);

        assertNotSame(actual.setTimeLimit(new Duration(2, TimeUnit.SECONDS)), actual);
        assertEquals(actual, expected);
    }

    @Test
    public void testGetters()
            throws Exception
    {
        Command command = new Command("a", "b", "c")
                .setDirectory("directory")
                .setSuccessfulExitCodes(33, 44)
                .setTimeLimit(5, TimeUnit.SECONDS);

        assertEquals(command.getCommand(), ImmutableList.of("a", "b", "c"));
        assertEquals(command.getDirectory(), new File("directory"));
        assertEquals(command.getSuccessfulExitCodes(), ImmutableSet.of(44, 33));
        assertEquals(command.getTimeLimit(), new Duration(5, TimeUnit.SECONDS));
    }


    @Test
    public void execSimple()
            throws Exception
    {
        assertEquals(new Command("bash", "-c", "set").setTimeLimit(1, TimeUnit.SECONDS).execute(executor), 0);
    }

    @Test(expectedExceptions = CommandTimeoutException.class)
    public void execTimeout()
            throws Exception
    {
        new Command("bash", "-c", "echo foo && sleep 15").setTimeLimit(1, TimeUnit.SECONDS).execute(executor);
    }

    @Test(expectedExceptions = CommandFailedException.class)
    public void execBadExitCode()
            throws Exception
    {
        new Command("bash", "-c", "exit 33").setTimeLimit(1, TimeUnit.SECONDS).execute(executor);
    }

    @Test
    public void execNonZeroSuccess()
            throws Exception
    {
        assertEquals(new Command("bash", "-c", "exit 33").setSuccessfulExitCodes(33).setTimeLimit(1, TimeUnit.SECONDS).execute(executor), 33);
    }

    @Test(expectedExceptions = CommandFailedException.class)
    public void execZeroExitFail()
            throws Exception
    {
        new Command("bash", "-c", "exit 0").setSuccessfulExitCodes(33).setTimeLimit(1, TimeUnit.SECONDS).execute(executor);
    }

    @Test(expectedExceptions = CommandFailedException.class)
    public void execBogusProcess()
            throws Exception
    {
        new Command("ab898wer98e7r98e7r98e7r98ew").setTimeLimit(1, TimeUnit.SECONDS).execute(executor);
    }

    @Test
    public void testEquivalence()
    {
        EquivalenceTester.check(
                asList(new Command("command"), new Command("command")),
                asList(new Command("command").setDirectory("foo"), new Command("command").setDirectory(new File("foo"))),
                asList(new Command("command").setTimeLimit(5, TimeUnit.SECONDS), new Command("command").setTimeLimit(new Duration(5, TimeUnit.SECONDS))),
                asList(new Command("command").setSuccessfulExitCodes(5, 6), new Command("command").setSuccessfulExitCodes(ImmutableSet.of(6, 5)))
        );
    }

}
