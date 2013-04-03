package io.airlift.airship.agent;

import com.google.common.base.Joiner;
import io.airlift.units.Duration;

import java.util.List;

public class LockTimeoutException extends RuntimeException
{
    public LockTimeoutException(Thread ownerThread, Duration lockWait, List<StackTraceElement> acquisitionLocation)
    {
        super(String.format("Could not obtain slot lock within %s held by %s thread is at \n%s\n",
                lockWait,
                ownerThread,
                acquisitionLocation == null ? null : Joiner.on("\n  at ").join(acquisitionLocation)));
    }

}
