package com.proofpoint.galaxy.agent;

import com.google.common.base.Charsets;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.Files;
import com.google.common.net.InetAddresses;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.inject.Inject;
import com.proofpoint.galaxy.shared.Command;
import com.proofpoint.galaxy.shared.CommandFailedException;
import com.proofpoint.galaxy.shared.SlotLifecycleState;
import com.proofpoint.http.server.HttpServerInfo;
import com.proofpoint.log.Logger;
import com.proofpoint.node.NodeInfo;
import com.proofpoint.units.Duration;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import static com.proofpoint.galaxy.shared.SlotLifecycleState.RUNNING;
import static com.proofpoint.galaxy.shared.SlotLifecycleState.STOPPED;
import static com.proofpoint.galaxy.shared.SlotLifecycleState.UNKNOWN;

public class LauncherLifecycleManager implements LifecycleManager
{
    private static final Logger log = Logger.get(LauncherLifecycleManager.class);

    private final Executor executor;
    private final InetAddress internalIp;
    private final String externalAddress;
    private final Duration launcherTimeout;
    private final Duration stopTimeout;
    private final String environment;
    private final InetAddress bindIp;
    private final URI serviceInventoryUri;

    @Inject
    public LauncherLifecycleManager(AgentConfig config, NodeInfo nodeInfo, HttpServerInfo httpServerInfo)
    {
        this(nodeInfo.getEnvironment(),
                nodeInfo.getInternalIp(),
                nodeInfo.getExternalAddress(),
                nodeInfo.getBindIp(),
                config.getLauncherTimeout(),
                config.getLauncherStopTimeout(),
                (httpServerInfo.getHttpsUri() != null ? httpServerInfo.getHttpsUri() : httpServerInfo.getHttpUri()).resolve("/v1/serviceInventory")
        );
    }

    public LauncherLifecycleManager(String environment,
            InetAddress internalIp,
            String externalAddress,
            InetAddress bindIp,
            Duration launcherTimeout,
            Duration launcherStopTimeout,
            URI serviceInventoryUri)
    {
        this.launcherTimeout = launcherTimeout;
        stopTimeout = launcherStopTimeout;

        executor = Executors.newCachedThreadPool(new ThreadFactoryBuilder().setDaemon(true).setNameFormat("launcher-command-%s").build());
        this.environment = environment;
        this.internalIp = internalIp;
        this.externalAddress = externalAddress;
        this.bindIp = bindIp;

        this.serviceInventoryUri = serviceInventoryUri;
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
                .addEnvironment("HOME", deployment.getDataDir().getAbsolutePath());

        return command;
    }

    @Override
    public void updateNodeConfig(Deployment deployment)
    {
        ImmutableMap.Builder<String, String> map = ImmutableMap.builder();

        map.put("node.environment", environment);
        map.put("node.id", deployment.getNodeId().toString());
        map.put("node.location", deployment.getLocation());
        map.put("node.data-dir", deployment.getDataDir().getAbsolutePath());
        map.put("node.binary-spec", deployment.getAssignment().getBinary());
        map.put("node.config-spec", deployment.getAssignment().getConfig());

        if (internalIp != null) {
            map.put("node.ip", InetAddresses.toAddrString(internalIp));
        }

        if (externalAddress != null) {
            map.put("node.external-address", externalAddress);
        }

        // add ip only if explicitly set on the agent
        if (bindIp != null && InetAddresses.coerceToInteger(bindIp) != 0) {
            map.put("node.ip", bindIp.getHostAddress());
        }

        // add service inventory uri
        map.put("service-inventory.uri", serviceInventoryUri.toString());

        File nodeConfig = new File(deployment.getDeploymentDir(), "etc/node.properties");
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
}
