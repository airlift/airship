/**
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.proofpoint.galaxy.agent;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.proofpoint.galaxy.shared.SlotLifecycleState;
import com.proofpoint.galaxy.shared.SlotStatus;
import com.proofpoint.galaxy.shared.Installation;
import com.proofpoint.log.Logger;
import com.proofpoint.units.Duration;

import java.net.URI;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;

import static com.proofpoint.galaxy.shared.SlotLifecycleState.STOPPED;
import static com.proofpoint.galaxy.shared.SlotLifecycleState.TERMINATED;
import static com.proofpoint.galaxy.shared.SlotLifecycleState.UNASSIGNED;
import static com.proofpoint.galaxy.shared.SlotLifecycleState.UNKNOWN;

public class DeploymentSlot implements Slot
{
    private static final Logger log = Logger.get(DeploymentSlot.class);

    private final UUID id;
    private final String name;
    private final URI self;
    private final Duration lockWait;
    private final DeploymentManager deploymentManager;
    private final LifecycleManager lifecycleManager;
    private final AtomicReference<SlotStatus> lastSlotStatus = new AtomicReference<SlotStatus>();
    private boolean terminated;

    private final ReentrantLock lock = new ReentrantLock();
    private volatile Thread lockOwner;
    private volatile List<StackTraceElement> lockAcquisitionLocation;

    public DeploymentSlot(String name, AgentConfig config, URI self, DeploymentManager deploymentManager, LifecycleManager lifecycleManager)
    {
        Preconditions.checkNotNull(name, "name is null");
        Preconditions.checkNotNull(config, "config is null");
        Preconditions.checkNotNull(deploymentManager, "deploymentManager is null");
        Preconditions.checkNotNull(lifecycleManager, "lifecycleManager is null");

        this.name = name;
        this.deploymentManager = deploymentManager;
        this.lifecycleManager = lifecycleManager;

        lockWait = config.getMaxLockWait();
        id = deploymentManager.getSlotId();
        this.self = self;

        lastSlotStatus.set(new SlotStatus(id, name, self, UNKNOWN, null));
    }

    @Override
    public UUID getId()
    {
        return id;
    }

    @Override
    public String getName()
    {
        return name;
    }

    @Override
    public URI getSelf()
    {
        return self;
    }

    @Override
    public SlotStatus assign(Installation installation)
    {
        Preconditions.checkNotNull(installation, "installation is null");

        lock();
        try {
            Preconditions.checkState(!terminated, "Slot has been terminated");

            log.info("Becoming %s with %s", installation.getAssignment().getBinary(), installation.getAssignment().getConfig());

            // stop current server
            Deployment oldDeployment = deploymentManager.getDeployment();
            if (oldDeployment != null) {
                SlotLifecycleState state = lifecycleManager.stop(oldDeployment);
                if (state != STOPPED) {
                    // todo error
                }

                // remove the deployment
                deploymentManager.clear();
            }


            // deploy new server
            deploymentManager.install(installation);

            // create node config file
            lifecycleManager.updateNodeConfig(deploymentManager.getDeployment());

            // inform everyone else of the change
            // todo should this be done after the lock is released
            // @event_dispatcher.dispatch_become_success_event status
            // announce
            SlotStatus slotStatus = new SlotStatus(id, name, self, STOPPED, installation.getAssignment());
            lastSlotStatus.set(slotStatus);
            return slotStatus;
        }
        finally {
            unlock();
        }
    }

    @Override
    public SlotStatus clear()
    {
        lock();
        try {
            Preconditions.checkState(!terminated, "Slot has been terminated");

            Deployment activeDeployment = deploymentManager.getDeployment();
            if (activeDeployment != null) {

                // Stop server
                try {
                    SlotLifecycleState state = lifecycleManager.stop(activeDeployment);
                    if (state != STOPPED) {
                        // todo error
                    }
                }
                catch (RuntimeException e) {
                    if (e.getMessage().contains("invalid option: --data")) {
                        // so we added a new option and old binaries won't clear, which is super annoying
                        // skip these binaries
                    }
                    else {
                        throw e;
                    }
                }

                // remove deployment
                deploymentManager.clear();
            }

            SlotStatus slotStatus = new SlotStatus(id, name, self);
            lastSlotStatus.set(slotStatus);
            return slotStatus;

        }
        finally {
            unlock();
        }
    }

    @Override
    public SlotStatus terminate()
    {
        lock();
        try {
            if (!terminated) {

                SlotStatus status = status();
                if (status.getState() != STOPPED && status.getState() != UNASSIGNED) {
                    return status;
                }

                // terminate the slot
                deploymentManager.terminate();
                terminated = true;
            }
            SlotStatus slotStatus = new SlotStatus(id, name, self, TERMINATED, null);
            lastSlotStatus.set(slotStatus);
            return slotStatus;
        }
        finally {
            unlock();
        }
    }

    @Override
    public SlotStatus status()
    {
        try {
            lock();
        }
        catch (LockTimeoutException e) {
            // could not get the lock because there is an operation in progress
            // just return the last state we saw
            // todo consider adding "in-process" states like starting
            return lastSlotStatus.get();
        }
        try {
            if (terminated) {
                return new SlotStatus(id, name, self, TERMINATED, null);
            }

            Deployment activeDeployment = deploymentManager.getDeployment();
            if (activeDeployment == null) {
                return new SlotStatus(id, name, self);
            }

            SlotLifecycleState state = lifecycleManager.status(activeDeployment);
            return new SlotStatus(id, name, self, state, activeDeployment.getAssignment());
        }
        finally {
            unlock();
        }
    }

    @Override
    public SlotStatus start()
    {
        lock();
        try {
            Preconditions.checkState(!terminated, "Slot has been terminated");

            Deployment activeDeployment = deploymentManager.getDeployment();
            if (activeDeployment == null) {
                throw new IllegalStateException("Slot can not be started because the slot is not assigned");
            }

            SlotLifecycleState state = lifecycleManager.start(activeDeployment);

            SlotStatus slotStatus = new SlotStatus(id, name, self, state, activeDeployment.getAssignment());
            lastSlotStatus.set(slotStatus);

            return slotStatus;
        }
        finally {
            unlock();
        }
    }

    @Override
    public SlotStatus restart()
    {
        lock();
        try {
            Preconditions.checkState(!terminated, "Slot has been terminated");

            Deployment activeDeployment = deploymentManager.getDeployment();
            if (activeDeployment == null) {
                throw new IllegalStateException("Slot can not be restarted because the slot is not assigned");
            }

            SlotLifecycleState state = lifecycleManager.restart(activeDeployment);

            SlotStatus slotStatus = new SlotStatus(id, name, self, state, activeDeployment.getAssignment());
            lastSlotStatus.set(slotStatus);

            return slotStatus;
        }
        finally {
            unlock();
        }
    }

    @Override
    public SlotStatus stop()
    {
        lock();
        try {
            Preconditions.checkState(!terminated, "Slot has been terminated");

            Deployment activeDeployment = deploymentManager.getDeployment();
            if (activeDeployment == null) {
                throw new IllegalStateException("Slot can not be stopped because the slot is not assigned");
            }

            SlotLifecycleState state = lifecycleManager.stop(activeDeployment);

            SlotStatus slotStatus = new SlotStatus(id, name, self, state, activeDeployment.getAssignment());
            lastSlotStatus.set(slotStatus);

            return slotStatus;
        }
        finally {
            unlock();
        }
    }


    private void lock()
    {
        try {
            if (!lock.tryLock((long) lockWait.toMillis(), TimeUnit.MILLISECONDS)) {
                throw new LockTimeoutException(lockOwner, lockWait, lockAcquisitionLocation);
            }

            // capture the location where the lock was acquired
            lockAcquisitionLocation = ImmutableList.copyOf(new Exception("lock acquired HERE").fillInStackTrace().getStackTrace());
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }
    }


    private void unlock()
    {
        lockOwner = null;
        lockAcquisitionLocation = null;
        lock.unlock();
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

        DeploymentSlot slot = (DeploymentSlot) o;

        if (!id.equals(slot.id)) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode()
    {
        return id.hashCode();
    }

    @Override
    public String toString()
    {
        final StringBuilder sb = new StringBuilder();
        sb.append("Slot");
        sb.append("{slotId=").append(id);
        sb.append(", name='").append(name).append('\'');
        sb.append('}');
        return sb.toString();
    }
}
