package com.proofpoint.galaxy.agent;

import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.net.InetAddresses;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.inject.Inject;
import com.proofpoint.discovery.client.DiscoveryClientConfig;
import com.proofpoint.discovery.client.ServiceSelectorConfig;
import com.proofpoint.galaxy.shared.Command;
import com.proofpoint.galaxy.shared.CommandFailedException;
import com.proofpoint.galaxy.shared.ConfigSpec;
import com.proofpoint.galaxy.shared.SlotLifecycleState;
import com.proofpoint.log.Logger;
import com.proofpoint.node.NodeInfo;
import com.proofpoint.units.Duration;

import java.io.File;
import java.net.URI;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import static com.proofpoint.galaxy.shared.SlotLifecycleState.RUNNING;
import static com.proofpoint.galaxy.shared.SlotLifecycleState.STOPPED;
import static com.proofpoint.galaxy.shared.SlotLifecycleState.UNKNOWN;

public class LauncherLifecycleManager implements LifecycleManager
{
    private final Executor executor;
    private final NodeInfo nodeInfo;
    private final URI discoveryServiceURI;
    private final Duration launcherTimeout;
    private final Duration stopTimeout;
    private final List<String> generalNodeArgs;

    @Inject
    public LauncherLifecycleManager(AgentConfig config, NodeInfo nodeInfo, DiscoveryClientConfig discoveryClientConfig)
    {
        Preconditions.checkNotNull(config, "config is null");
        Preconditions.checkNotNull(nodeInfo, "nodeInfo is null");
        Preconditions.checkNotNull(discoveryClientConfig, "discoveryClientConfig is null");

        launcherTimeout = config.getLauncherTimeout();
        stopTimeout = config.getLauncherStopTimeout();

        this.nodeInfo = nodeInfo;
        this.discoveryServiceURI = discoveryClientConfig.getDiscoveryServiceURI();

        ImmutableList.Builder<String> nodeArgsBuilder = ImmutableList.builder();
        nodeArgsBuilder.add("-Dnode.environment=" + nodeInfo.getEnvironment());

        // add ip only if explicitly set on the agent
        if (InetAddresses.coerceToInteger(nodeInfo.getBindIp()) != 0) {
            nodeArgsBuilder.add("-Dnode.ip=" + nodeInfo.getBindIp());
        }
        generalNodeArgs = nodeArgsBuilder.build();

        executor = Executors.newCachedThreadPool(new ThreadFactoryBuilder().setDaemon(true).setNameFormat("launcher-command-%s").build());
    }

    @Override
    public SlotLifecycleState status(Deployment deployment)
    {
        try {
            int exitCode = createCommand("status", deployment, launcherTimeout)
                    .setSuccessfulExitCodes(0, 1, 2, 3)
                    .execute(executor);
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

    private static final Logger log = Logger.get(LauncherLifecycleManager.class);
    @Override
    public SlotLifecycleState start(Deployment deployment)
    {
        Command command = createCommand("start", deployment, launcherTimeout);
        try {
            command = addEnvironmentData(command, deployment);
            command.execute(executor);
            return RUNNING;
        }
        catch (CommandFailedException e) {
            log.error("ENVIRONMENT:\n    %s", Joiner.on("\n    ").withKeyValueSeparator("=").join(command.getEnvironment()));
            throw new RuntimeException("start failed: " + e.getMessage());
        }
    }

    @Override
    public SlotLifecycleState restart(Deployment deployment)
    {
        try {
            Command command = createCommand("restart", deployment, stopTimeout);
            command = addEnvironmentData(command, deployment);
            command.execute(executor);
            return RUNNING;
        }
        catch (CommandFailedException e) {
            throw new RuntimeException("restart failed: " + e.getMessage());
        }
    }

    @Override
    public SlotLifecycleState stop(Deployment deployment)
    {
        try {
            createCommand("stop", deployment, stopTimeout).execute(executor);
            return STOPPED;
        }
        catch (CommandFailedException e) {
            throw new RuntimeException("stop failed: " + e.getMessage());
        }
    }

    private Command createCommand(String commandName, Deployment deployment, Duration timeLimit)
    {

        File launcherScript = new File(new File(deployment.getDeploymentDir(), "bin"), "launcher");

        Command command = new Command(launcherScript.getAbsolutePath(), commandName)
                .setDirectory(deployment.getDataDir())
                .setTimeLimit(timeLimit)
                .addEnvironment("HOME", deployment.getDataDir().getAbsolutePath())
                .addArgs("--data").addArgs(deployment.getDataDir().getAbsolutePath());

        return command;
    }

    private Command addEnvironmentData(Command command, Deployment deployment)
    {
        return command.addArgs(generalNodeArgs)
                .addArgs("-Dnode.pool=" + getPool(deployment.getAssignment().getConfig()))
                .addArgs("-Dnode.id=" + deployment.getNodeId())
                .addArgs("-Dnode.location=" + nodeInfo.getLocation() + "/" + deployment.getSlotName())
                .addArgs("-Ddiscovery.uri=" + discoveryServiceURI);
    }

    private static String getPool(ConfigSpec config)
    {
        // todo remove this when default pool is "general"
        String pool = config.getPool();
        if (pool != null) {
            return pool;
        } else {
            return ServiceSelectorConfig.DEFAULT_POOL;
        }
    }
}
