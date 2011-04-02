package com.proofpoint.galaxy.agent;

import com.google.common.base.Preconditions;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.inject.Inject;
import com.proofpoint.galaxy.shared.Command;
import com.proofpoint.galaxy.shared.CommandFailedException;
import com.proofpoint.galaxy.shared.LifecycleState;

import java.io.File;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import static com.proofpoint.galaxy.shared.LifecycleState.RUNNING;
import static com.proofpoint.galaxy.shared.LifecycleState.STOPPED;
import static com.proofpoint.galaxy.shared.LifecycleState.UNKNOWN;

public class LauncherLifecycleManager implements LifecycleManager
{
    private final Command status;
    private final Command start;
    private final Command stop;
    private final Command restart;

    private final Executor executor;

    @Inject
    public LauncherLifecycleManager(AgentConfig config)
    {
        Preconditions.checkNotNull(config, "config is null");

        status = new Command("./launcher", "status").setTimeLimit(config.getLauncherTimeout()).setSuccessfulExitCodes(0, 1, 2, 3);
        start = new Command("./launcher", "start").setTimeLimit(config.getLauncherTimeout());
        stop = new Command("./launcher", "stop").setTimeLimit(config.getLauncherTimeout());
        restart = new Command("./launcher", "restart").setTimeLimit(config.getLauncherTimeout());
        executor = Executors.newCachedThreadPool(new ThreadFactoryBuilder().setDaemon(true).setNameFormat("launcher-command-%s").build());
    }

    @Override
    public LifecycleState status(Deployment deployment)
    {
        try {
            int exitCode = status.setDirectory(new File(deployment.getDeploymentDir(), "bin")).execute(executor);
            if (exitCode == 0) {
                return RUNNING;
            }
            else {
                return STOPPED;
            }
        }
        catch (CommandFailedException e) {
            return UNKNOWN;
        }
    }

    @Override
    public LifecycleState start(Deployment deployment)
    {
        try {
            start.setDirectory(new File(deployment.getDeploymentDir(), "bin")).execute(executor);
            return RUNNING;
        }
        catch (CommandFailedException e) {
            throw new RuntimeException("start failed: " + e.getMessage());
        }
    }

    @Override
    public LifecycleState restart(Deployment deployment)
    {
        try {
            restart.setDirectory(new File(deployment.getDeploymentDir(), "bin")).execute(executor);
            return RUNNING;
        }
        catch (CommandFailedException e) {
            throw new RuntimeException("restart failed: " + e.getMessage());
        }
    }

    @Override
    public LifecycleState stop(Deployment deployment)
    {
        try {
            stop.setDirectory(new File(deployment.getDeploymentDir(), "bin")).execute(executor);
            return STOPPED;
        }
        catch (CommandFailedException e) {
            throw new RuntimeException("stop failed: " + e.getMessage());
        }
    }
}
