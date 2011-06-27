package com.proofpoint.galaxy.agent;

import com.google.common.base.Charsets;
import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.Files;
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
import java.io.IOException;
import java.net.URI;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import static com.proofpoint.galaxy.shared.SlotLifecycleState.RUNNING;
import static com.proofpoint.galaxy.shared.SlotLifecycleState.STOPPED;
import static com.proofpoint.galaxy.shared.SlotLifecycleState.UNKNOWN;

public class LauncherLifecycleManager implements LifecycleManager
{
    private final Executor executor;
    private final Duration launcherTimeout;
    private final Duration stopTimeout;
    private final NodeInfo nodeInfo;
    private final URI discoveryServiceURI;

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
        updateNodeConfig(deployment);
        Command command = createCommand("start", deployment, launcherTimeout);
        try {
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
        updateNodeConfig(deployment);
        try {
            Command command = createCommand("restart", deployment, stopTimeout);
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
        updateNodeConfig(deployment);
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

    @Override
    public void updateNodeConfig(Deployment deployment)
    {
        ImmutableMap.Builder<String, String> map = ImmutableMap.builder();

        map.put("node.environment", nodeInfo.getEnvironment());
        map.put("node.pool", getPool(deployment.getAssignment().getConfig()));
        map.put("node.id", deployment.getNodeId().toString());
        map.put("node.location", nodeInfo.getLocation() + "/" + deployment.getSlotName());
        map.put("discovery.uri", discoveryServiceURI.toString());

        // add ip only if explicitly set on the agent
        if (InetAddresses.coerceToInteger(nodeInfo.getBindIp()) != 0) {
            map.put("node.ip", nodeInfo.getBindIp().getHostAddress());
        }

        File nodeConfig = new File(deployment.getDeploymentDir(), "env/node.config");
        nodeConfig.getParentFile().mkdir();

        try {
            String data = Joiner.on("\n").withKeyValueSeparator("=").join(map.build()) + "\n";
            Files.write(data, nodeConfig, Charsets.UTF_8);
        }
        catch (IOException e) {
            nodeConfig.delete();
            throw new RuntimeException("create node config failed: " + e.getMessage());
        }
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
