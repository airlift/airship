package com.proofpoint.galaxy.agent;

public class CommandTimeoutException extends CommandFailedException
{
    public CommandTimeoutException(Command command)
    {
        super(command, "did not complete in " + command.getTimeLimit(), null);
    }
}
