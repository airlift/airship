package com.proofpoint.galaxy.integration;

import com.google.common.base.Function;
import com.google.common.base.Preconditions;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.proofpoint.configuration.ConfigurationFactory;
import com.proofpoint.configuration.ConfigurationModule;
import com.proofpoint.discovery.client.testing.TestingDiscoveryModule;
import com.proofpoint.event.client.NullEventModule;
import com.proofpoint.galaxy.agent.Agent;
import com.proofpoint.galaxy.agent.AgentMainModule;
import com.proofpoint.galaxy.agent.Slot;
import com.proofpoint.galaxy.coordinator.Instance;
import com.proofpoint.galaxy.coordinator.Provisioner;
import com.proofpoint.galaxy.shared.AgentLifecycleState;
import com.proofpoint.galaxy.shared.AgentStatus;
import com.proofpoint.galaxy.shared.SlotStatus;
import com.proofpoint.http.server.testing.TestingHttpServer;
import com.proofpoint.http.server.testing.TestingHttpServerModule;
import com.proofpoint.jaxrs.JaxrsModule;
import com.proofpoint.json.JsonModule;
import com.proofpoint.node.NodeModule;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import static com.google.common.collect.Lists.newArrayList;
import static com.proofpoint.galaxy.shared.FileUtils.createTempDir;
import static com.proofpoint.galaxy.shared.FileUtils.deleteRecursively;

public class MockLocalProvisioner implements Provisioner
{
    private final Map<String, Instance> coordinators = new ConcurrentHashMap<String, Instance>();
    private final Map<String, AgentServer> agents = new ConcurrentHashMap<String, AgentServer>();
    private final AtomicInteger nextInstanceId = new AtomicInteger();

    public boolean autoStartInstances;

    public void addCoordinators(Instance... instances)
    {
        addCoordinators(ImmutableList.copyOf(instances));
    }

    public void addCoordinators(Iterable<Instance> instances)
    {
        for (Instance instance : instances) {
            coordinators.put(instance.getInstanceId(), instance);
        }
    }

    public void removeCoordinators(String... coordinatorIds)
    {
        removeCoordinators(ImmutableList.copyOf(coordinatorIds));
    }

    public void removeCoordinators(Iterable<String> coordinatorIds)
    {
        for (String coordinatorId : coordinatorIds) {
            coordinators.remove(coordinatorId);
        }
    }

    public void clearCoordinators()
    {
        coordinators.clear();
    }

    @Override
    public List<Instance> listCoordinators()
    {
        return ImmutableList.copyOf(coordinators.values());
    }

    @Override
    public List<Instance> provisionCoordinators(String coordinatorConfigSpec,
            int coordinatorCount,
            String instanceType,
            String availabilityZone,
            String ami,
            String keyPair,
            String securityGroup)
    {
        List<Instance> instances = newArrayList();
        for (int i = 0; i < coordinatorCount; i++) {
            String coordinatorInstanceId = String.format("i-%05d", nextInstanceId.incrementAndGet());
            String location = String.format("/mock/%s/coordinator", coordinatorInstanceId);
            Instance instance = new Instance(coordinatorInstanceId, instanceType, location, null, null);
            instances.add(instance);
        }
        addCoordinators(instances);

        if (autoStartInstances) {
            List<Instance> runningInstances = newArrayList();
            for (Instance instance : instances) {
                runningInstances.add(startCoordinator(instance.getInstanceId()));
            }
            instances = runningInstances;
        }

        return ImmutableList.copyOf(instances);
    }

    public Instance startCoordinator(String instanceId) {
        Instance instance = coordinators.get(instanceId);
        Preconditions.checkNotNull(instance, "instance is null");

        URI internalUri = URI.create("fake:/" + instanceId + "/internal");
        URI externalUri = URI.create("fake:/" + instanceId + "/external");
        Instance newCoordinatorInstance = new Instance(
                instanceId,
                instance.getInstanceType(),
                instance.getLocation(),
                internalUri,
                externalUri);

        coordinators.put(instanceId, newCoordinatorInstance);
        return newCoordinatorInstance;
    }

    @Override
    public List<Instance> listAgents()
    {
        return ImmutableList.copyOf(Iterables.transform(agents.values(), new GetInstanceFunction()));
    }

    @Override
    public List<Instance> provisionAgents(String agentConfig,
            int agentCount,
            String instanceType,
            String availabilityZone,
            String ami,
            String keyPair,
            String securityGroup)
    {
        List<Instance> instances = newArrayList();
        for (int i = 0; i < agentCount; i++) {
            String agentInstanceId = String.format("i-%05d", nextInstanceId.incrementAndGet());
            String location = String.format("/mock/%s/agent", agentInstanceId);

            AgentStatus agentStatus = new AgentStatus(null,
                    AgentLifecycleState.OFFLINE,
                    agentInstanceId,
                    null,
                    null,
                    location,
                    instanceType,
                    ImmutableList.<SlotStatus>of(),
                    ImmutableMap.<String, Integer>of());

            Instance instance = new Instance(agentStatus.getInstanceId(),
                    agentStatus.getInstanceType(),
                    agentStatus.getLocation(),
                    null,
                    null);
            AgentServer agentServer = new AgentServer(instance);
            instances.add(instance);
            if (autoStartInstances) {
                try {
                    agentServer.start();
                }
                catch (Exception e) {
                    throw Throwables.propagate(e);
                }
            }
            agents.put(agentInstanceId, agentServer);
        }

        return ImmutableList.copyOf(instances);
    }

    @Override
    public void terminateAgents(Iterable<String> instanceIds)
    {
        for (Entry<String, AgentServer> entry : agents.entrySet()) {
            entry.getValue().destroy();
            agents.remove(entry.getKey());
        }
    }

    public void clearAgents()
            throws Exception
    {
        terminateAgents(agents.keySet());
    }

    public ImmutableList<AgentServer> getAllAgents()
    {
        return ImmutableList.copyOf(agents.values());
    }

    public AgentServer getAgent(String instanceId)
    {
        return agents.get(instanceId);
    }

    public static class AgentServer
    {
        public static Map<String, Integer> AGENT_RESOURCES = ImmutableMap.<String, Integer>builder()
                .put("cpu", 16)
                .put("memory", 64000)
                .build();

        private Instance instance;
        private final File resourcesFile;
        private final File tempDir;
        private TestingHttpServer agentServer;
        private Agent agent;

        public AgentServer(Instance instance)
        {
            Preconditions.checkNotNull(instance, "instance is null");
            this.instance = instance;

            tempDir = createTempDir("agent");
            resourcesFile = new File(tempDir, "slots/galaxy-resources.properties");
            writeResources(AGENT_RESOURCES, resourcesFile);
        }

        public void start()
                throws Exception
        {
            Map<String, String> agentProperties = ImmutableMap.<String, String>builder()
                    .put("node.environment", "test")
                    .put("node.id", instance.getInstanceId())
                    .put("node.location", instance.getLocation())
                    .put("agent.slots-dir", new File(tempDir, "slots").getAbsolutePath())
                    .put("agent.resources-file", resourcesFile.getAbsolutePath())
                    .build();

            Injector agentInjector = Guice.createInjector(new NodeModule(),
                    new TestingHttpServerModule(),
                    new TestingDiscoveryModule(),
                    new JsonModule(),
                    new JaxrsModule(),
                    new NullEventModule(),
                    new AgentMainModule(),
                    new ConfigurationModule(new ConfigurationFactory(agentProperties)));

            agentServer = agentInjector.getInstance(TestingHttpServer.class);
            agent = agentInjector.getInstance(Agent.class);
            agentServer.start();

            // update instance URIs to new http server
            instance = new Instance(instance.getInstanceId(),
                    instance.getInstanceType(),
                    instance.getLocation(),
                    agentServer.getBaseUrl(),
                    agentServer.getBaseUrl());
        }

        public void stop()
        {
            if (agentServer != null) {
                for (Slot slot : agent.getAllSlots()) {
                    if (slot.status().getAssignment() != null) {
                        slot.stop();
                    }
                    agent.terminateSlot(slot.getId());
                }

                try {
                    agentServer.stop();
                }
                catch (Exception ignored) {
                }
            }

            // clear instance URIs
            instance = new Instance(instance.getInstanceId(),
                    instance.getInstanceType(),
                    instance.getLocation(),
                    null,
                    null);
        }

        public void destroy()
        {
            stop();

            if (tempDir != null) {
                deleteRecursively(tempDir);
            }
        }

        public String getInstanceId()
        {
            return instance.getInstanceId();
        }

        public Instance getInstance()
        {
            return instance;
        }

        public TestingHttpServer getAgentServer()
        {
            return agentServer;
        }

        public Agent getAgent()
        {
            return agent;
        }

        public static void writeResources(Map<String, Integer> resources, File resourcesFile)
        {
            Properties properties = new Properties();
            for (Entry<String, Integer> entry : resources.entrySet()) {
                properties.setProperty(entry.getKey(), String.valueOf(entry.getValue()));
            }
            resourcesFile.getParentFile().mkdirs();
            try {
                FileOutputStream out = new FileOutputStream(resourcesFile);
                try {
                    properties.store(out, "");
                }
                finally {
                    out.close();
                }
            }
            catch (IOException e) {
                throw Throwables.propagate(e);
            }
        }
    }

    private static class GetInstanceFunction implements Function<AgentServer, Instance>
    {
        @Override
        public Instance apply(AgentServer agentServer)
        {
            return agentServer.getInstance();
        }
    }
}
