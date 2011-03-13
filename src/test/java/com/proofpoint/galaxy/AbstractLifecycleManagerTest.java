package com.proofpoint.galaxy;

import org.testng.annotations.Test;

import static com.proofpoint.galaxy.LifecycleState.RUNNING;
import static com.proofpoint.galaxy.LifecycleState.STOPPED;
import static org.testng.Assert.assertEquals;

public abstract class AbstractLifecycleManagerTest
{
    protected LifecycleManager manager;
    protected Deployment apple;
    protected Deployment banana;

    @Test
    public void testStateMachine()
    {

        // default state is stopped
        assertEquals(manager.status(apple), STOPPED);

        // stopped.start => running
        assertEquals(manager.start(apple), RUNNING);
        assertEquals(manager.status(apple), RUNNING);

        // running.start => running
        assertEquals(manager.start(apple), RUNNING);
        assertEquals(manager.status(apple), RUNNING);

        // running.stop => stopped
        assertEquals(manager.stop(apple), STOPPED);
        assertEquals(manager.status(apple), STOPPED);

        // stopped.stop => stopped
        assertEquals(manager.stop(apple), STOPPED);
        assertEquals(manager.status(apple), STOPPED);

        // stopped.restart => running
        assertEquals(manager.restart(apple), RUNNING);
        assertEquals(manager.status(apple), RUNNING);

        // running.restart => running
        assertEquals(manager.restart(apple), RUNNING);
        assertEquals(manager.status(apple), RUNNING);
    }

    @Test
    public void testIsolation()
    {
        // default state is stopped
        assertEquals(manager.status(apple), STOPPED);
        assertEquals(manager.status(banana), STOPPED);

        // start 1 doesn't effect 2
        assertEquals(manager.start(apple), RUNNING);
        assertEquals(manager.status(apple), RUNNING);
        assertEquals(manager.status(banana), STOPPED);

        // now start 2
        assertEquals(manager.start(banana), RUNNING);
        assertEquals(manager.status(banana), RUNNING);

        // stop 1 doesn't effect 2
        assertEquals(manager.stop(apple), STOPPED);
        assertEquals(manager.status(apple), STOPPED);
        assertEquals(manager.status(banana), RUNNING);

        // restart 2 doesn't effect 1
        assertEquals(manager.restart(banana), RUNNING);
        assertEquals(manager.status(banana), RUNNING);
        assertEquals(manager.status(apple), STOPPED);

        // restart 1 doesn't effect 2
        assertEquals(manager.restart(apple), RUNNING);
        assertEquals(manager.status(apple), RUNNING);
        assertEquals(manager.status(banana), RUNNING);
    }
}
