package com.proofpoint.galaxy.agent;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.net.InetAddresses;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.inject.Inject;
import com.proofpoint.experimental.discovery.client.DiscoveryClientConfig;
import com.proofpoint.experimental.discovery.client.ServiceSelectorConfig;
import com.proofpoint.galaxy.shared.Command;
import com.proofpoint.galaxy.shared.CommandFailedException;
import com.proofpoint.galaxy.shared.ConfigSpec;
import com.proofpoint.galaxy.shared.LifecycleState;
import com.proofpoint.node.NodeInfo;
import com.proofpoint.units.Duration;

import java.io.File;
import java.net.URI;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import static com.proofpoint.galaxy.shared.LifecycleState.RUNNING;
import static com.proofpoint.galaxy.shared.LifecycleState.STOPPED;
import static com.proofpoint.galaxy.shared.LifecycleState.UNKNOWN;
import static java.lang.String.format;

public class LauncherLifecycleManager implements LifecycleManager
{
    private final Executor executor;
    private final NodeInfo nodeInfo;
    private final URI discoveryServiceURI;
    private final Duration launcherTimeout;
    private final Duration stopTimeout;
    private final List<String> generalNodeArgs;
    private final File baseDataDir;

    @Inject
    public LauncherLifecycleManager(AgentConfig config, NodeInfo nodeInfo, DiscoveryClientConfig discoveryClientConfig)
    {
        Preconditions.checkNotNull(config, "config is null");
        Preconditions.checkNotNull(nodeInfo, "nodeInfo is null");
        Preconditions.checkNotNull(discoveryClientConfig, "discoveryClientConfig is null");

        launcherTimeout = config.getLauncherTimeout();
        stopTimeout = config.getLauncherStopTimeout();
        baseDataDir = new File(config.getDataDir());
        if (!baseDataDir.isDirectory()) {
            baseDataDir.mkdirs();
            Preconditions.checkArgument(baseDataDir.isDirectory(), format("Data directory %s is not a directory", baseDataDir));
        }

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
    public LifecycleState status(Deployment deployment)
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

    @Override
    public LifecycleState start(Deployment deployment)
    {
        try {
            Command command = createCommand("start", deployment, launcherTimeout);
            command = addEnvironmentData(command, deployment);
            command.execute(executor);
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
    public LifecycleState stop(Deployment deployment)
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
        ConfigSpec config = deployment.getAssignment().getConfig();
        File dataDir = new File(new File(baseDataDir, config.getComponent()), getPool(config));
        if (!dataDir.isDirectory()) {
            dataDir.mkdirs();
            Preconditions.checkArgument(dataDir.isDirectory(), format("Data directory %s is not a directory", dataDir));
        }

        File launcherScript = new File(new File(deployment.getDeploymentDir(), "bin"), "launcher");

        Command command = new Command(launcherScript.getAbsolutePath(), commandName)
                .setDirectory(dataDir)
                .setTimeLimit(timeLimit)
                .addArgs("--data").addArgs(dataDir.getAbsolutePath());

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
