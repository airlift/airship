package com.proofpoint.galaxy.agent;

import com.google.common.base.Charsets;
import com.google.common.base.Preconditions;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.io.CharStreams;
import com.google.common.primitives.Ints;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListenableFutureTask;
import com.proofpoint.units.Duration;

import javax.annotation.concurrent.Immutable;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Immutable
public class Command
{
    private static final ImmutableSet<Integer> DEFAULT_SUCCESSFUL_EXIT_CODES = ImmutableSet.of(0);
    private static final File DEFAULT_DIRECTORY = new File(".").getAbsoluteFile();
    private static final Duration DEFAULT_TIME_LIMIT = new Duration(365, TimeUnit.DAYS);

    private final List<String> command;
    private final Set<Integer> successfulExitCodes;
    private final File directory;
    private final Duration timeLimit;

    public Command(String... command)
    {
        this(ImmutableList.copyOf(command), DEFAULT_SUCCESSFUL_EXIT_CODES, DEFAULT_DIRECTORY, DEFAULT_TIME_LIMIT);
    }

    public Command(List<String> command, Set<Integer> successfulExitCodes, File directory, Duration timeLimit)
    {
        Preconditions.checkNotNull(command, "command is null");
        Preconditions.checkArgument(!command.isEmpty(), "command is empty");
        Preconditions.checkNotNull(successfulExitCodes, "successfulExitCodes is null");
        Preconditions.checkArgument(!successfulExitCodes.isEmpty(), "successfulExitCodes is empty");
        Preconditions.checkNotNull(directory, "directory is null");
        Preconditions.checkNotNull(timeLimit, "timeLimit is null");

        this.command = ImmutableList.copyOf(command);

        // these have default so are required
        this.successfulExitCodes = ImmutableSet.copyOf(successfulExitCodes);
        this.directory = directory;
        this.timeLimit = timeLimit;
    }

    public List<String> getCommand()
    {
        return command;
    }

    public Set<Integer> getSuccessfulExitCodes()
    {
        return successfulExitCodes;
    }

    public Command setSuccessfulExitCodes(int... successfulExitCodes)
    {
        Preconditions.checkNotNull(successfulExitCodes, "successfulExitCodes is null");
        return setSuccessfulExitCodes(ImmutableSet.copyOf(Ints.asList(successfulExitCodes)));
    }

    public Command setSuccessfulExitCodes(Set<Integer> successfulExitCodes)
    {
        Preconditions.checkNotNull(successfulExitCodes, "successfulExitCodes is null");
        Preconditions.checkArgument(!successfulExitCodes.isEmpty(), "successfulExitCodes is empty");
        return new Command(command, successfulExitCodes, directory, timeLimit);
    }

    public File getDirectory()
    {
        return directory;
    }

    public Command setDirectory(String directory)
    {
        Preconditions.checkNotNull(directory, "directory is null");
        return setDirectory(new File(directory));
    }

    public Command setDirectory(File directory)
    {
        Preconditions.checkNotNull(directory, "directory is null");
        return new Command(command, successfulExitCodes, directory, timeLimit);
    }

    public Duration getTimeLimit()
    {
        return timeLimit;
    }

    public Command setTimeLimit(double value, TimeUnit timeUnit)
    {
        return setTimeLimit(new Duration(value, timeUnit));
    }

    public Command setTimeLimit(Duration timeLimit)
    {
        Preconditions.checkNotNull(timeLimit, "timeLimit is null");
        return new Command(command, successfulExitCodes, directory, timeLimit);
    }

    public int execute(Executor executor)
            throws CommandFailedException
    {
        Preconditions.checkNotNull(executor, "executor is null");
        Preconditions.checkNotNull(command, "command is null");

        ProcessCallable processCallable = new ProcessCallable(this, executor);
        Future<Integer> future = submit(executor, processCallable);

        try {
            Integer result = future.get((long) timeLimit.toMillis(), TimeUnit.MILLISECONDS);
            return result;
        }
        catch (ExecutionException e) {
            Throwables.propagateIfPossible(e.getCause(), CommandFailedException.class);
            Throwable cause = e.getCause();
            if (cause == null) {
                cause = e;
            }
            throw new CommandFailedException(this, "unexpected exception", cause);
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new CommandFailedException(this, "interrupted", e);
        }
        catch (TimeoutException e) {
            future.cancel(true);
            throw new CommandTimeoutException(this);
        }
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        Command other = (Command) o;

        if (!command.equals(other.command)) {
            return false;
        }
        if (!directory.equals(other.directory)) {
            return false;
        }
        if (!successfulExitCodes.equals(other.successfulExitCodes)) {
            return false;
        }
        if (!timeLimit.equals(other.timeLimit)) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode()
    {
        int result = command.hashCode();
        result = 31 * result + successfulExitCodes.hashCode();
        result = 31 * result + directory.hashCode();
        result = 31 * result + timeLimit.hashCode();
        return result;
    }

    @Override
    public String toString()
    {
        final StringBuffer sb = new StringBuffer();
        sb.append("Command");
        sb.append("{command=").append(command);
        sb.append(", successfulExitCodes=").append(successfulExitCodes);
        sb.append(", directory=").append(directory);
        sb.append(", timeLimit=").append(timeLimit);
        sb.append('}');
        return sb.toString();
    }

    private static class ProcessCallable implements Callable<Integer>
    {
        private final Command command;
        private final Executor executor;

        public ProcessCallable(Command command, Executor executor)
        {
            this.command = command;
            this.executor = executor;
        }

        @Override
        public Integer call()
                throws CommandFailedException, InterruptedException
        {
            ProcessBuilder processBuilder = new ProcessBuilder(command.getCommand());
            processBuilder.directory(command.getDirectory());
            processBuilder.redirectErrorStream(true);

            final Process process;
            try {
                process = processBuilder.start();
            }
            catch (IOException e) {
                throw new CommandFailedException(command, "failed to start", e);
            }

            Future<String> outputFuture = submit(executor, new Callable<String>()
            {
                @Override
                public String call()
                        throws IOException
                {
                    String out = CharStreams.toString(new InputStreamReader(process.getInputStream(), Charsets.UTF_8));
                    return out;
                }
            });

            try {
                int exitCode = process.waitFor();
                if (!command.getSuccessfulExitCodes().contains(exitCode)) {
                    String out = getOutput(outputFuture);
                    throw new CommandFailedException(command, exitCode, out);
                }
                return exitCode;
            }
            catch (InterruptedException e) {
                outputFuture.cancel(true);
                process.destroy();
                throw e;
            }
            finally {
                getOutput(outputFuture);
            }
        }

        private String getOutput(Future<String> outputFuture)
        {
            if (outputFuture.isCancelled()) {
                return null;
            }

            try {
                String output = outputFuture.get();
                return output;
            }
            catch (Exception ignored) {
                return null;
            }
            finally {
                outputFuture.cancel(true);
            }
        }
    }

    private static <T> ListenableFuture<T> submit(Executor executor, Callable<T> task)
    {
        ListenableFutureTask<T> future = new ListenableFutureTask<T>(task);
        executor.execute(future);
        return future;
    }
}
