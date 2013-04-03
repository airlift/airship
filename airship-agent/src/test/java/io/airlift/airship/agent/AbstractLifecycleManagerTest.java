package io.airlift.airship.agent;

import org.testng.annotations.Test;

import static io.airlift.airship.shared.SlotLifecycleState.RUNNING;
import static io.airlift.airship.shared.SlotLifecycleState.STOPPED;
import static org.testng.Assert.assertEquals;

public abstract class AbstractLifecycleManagerTest
{
    protected LifecycleManager manager;
    protected Deployment appleDeployment;
    protected Deployment bananaDeployment;

    @Test
    public void testStateMachine()
    {

        // default state is stopped
        assertEquals(manager.status(appleDeployment), STOPPED);

        // stopped.start => running
        assertEquals(manager.start(appleDeployment), RUNNING);
        assertEquals(manager.status(appleDeployment), RUNNING);

        // running.start => running
        assertEquals(manager.start(appleDeployment), RUNNING);
        assertEquals(manager.status(appleDeployment), RUNNING);

        // running.stop => stopped
        assertEquals(manager.stop(appleDeployment), STOPPED);
        assertEquals(manager.status(appleDeployment), STOPPED);

        // stopped.stop => stopped
        assertEquals(manager.stop(appleDeployment), STOPPED);
        assertEquals(manager.status(appleDeployment), STOPPED);

        // stopped.restart => running
        assertEquals(manager.restart(appleDeployment), RUNNING);
        assertEquals(manager.status(appleDeployment), RUNNING);

        // running.restart => running
        assertEquals(manager.restart(appleDeployment), RUNNING);
        assertEquals(manager.status(appleDeployment), RUNNING);

        // running.kill => stopped
        assertEquals(manager.kill(appleDeployment), STOPPED);
        assertEquals(manager.status(appleDeployment), STOPPED);

        // stopped.kill => stopped
        assertEquals(manager.kill(appleDeployment), STOPPED);
        assertEquals(manager.status(appleDeployment), STOPPED);

    }

    @Test
    public void testIsolation()
    {
        // default state is stopped
        assertEquals(manager.status(appleDeployment), STOPPED);
        assertEquals(manager.status(bananaDeployment), STOPPED);

        // start 1 doesn't effect 2
        assertEquals(manager.start(appleDeployment), RUNNING);
        assertEquals(manager.status(appleDeployment), RUNNING);
        assertEquals(manager.status(bananaDeployment), STOPPED);

        // now start 2
        assertEquals(manager.start(bananaDeployment), RUNNING);
        assertEquals(manager.status(bananaDeployment), RUNNING);

        // stop 1 doesn't effect 2
        assertEquals(manager.stop(appleDeployment), STOPPED);
        assertEquals(manager.status(appleDeployment), STOPPED);
        assertEquals(manager.status(bananaDeployment), RUNNING);

        // restart 2 doesn't effect 1
        assertEquals(manager.restart(bananaDeployment), RUNNING);
        assertEquals(manager.status(bananaDeployment), RUNNING);
        assertEquals(manager.status(appleDeployment), STOPPED);

        // restart 1 doesn't effect 2
        assertEquals(manager.restart(appleDeployment), RUNNING);
        assertEquals(manager.status(appleDeployment), RUNNING);
        assertEquals(manager.status(bananaDeployment), RUNNING);
    }
}
