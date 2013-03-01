package io.airlift.airship.integration;

import com.google.common.base.Function;
import com.google.common.base.Preconditions;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.inject.Guice;
import com.google.inject.Injector;
import io.airlift.airship.agent.Agent;
import io.airlift.airship.agent.AgentMainModule;
import io.airlift.airship.agent.Slot;
import io.airlift.airship.coordinator.Coordinator;
import io.airlift.airship.coordinator.CoordinatorMainModule;
import io.airlift.airship.coordinator.Instance;
import io.airlift.airship.coordinator.Provisioner;
import io.airlift.airship.coordinator.StaticProvisionerModule;
import io.airlift.configuration.ConfigurationFactory;
import io.airlift.configuration.ConfigurationModule;
import io.airlift.discovery.client.testing.TestingDiscoveryModule;
import io.airlift.event.client.NullEventModule;
import io.airlift.http.server.testing.TestingHttpServer;
import io.airlift.http.server.testing.TestingHttpServerModule;
import io.airlift.jaxrs.JaxrsModule;
import io.airlift.json.JsonModule;
import io.airlift.node.NodeModule;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import static com.google.common.collect.Lists.newArrayList;
import static io.airlift.airship.shared.FileUtils.createTempDir;
import static io.airlift.airship.shared.FileUtils.deleteRecursively;

public class MockLocalProvisioner implements Provisioner
{
    private final Map<String, CoordinatorServer> coordinators = new ConcurrentHashMap<>();
    private final Map<String, AgentServer> agents = new ConcurrentHashMap<>();
    private final AtomicInteger nextInstanceId = new AtomicInteger();

    public boolean autoStartInstances;

    @Override
    public List<Instance> listCoordinators()
    {
        return ImmutableList.copyOf(Iterables.transform(coordinators.values(), new GetInstanceFunction()));
    }

    @Override
    public List<Instance> provisionCoordinators(String coordinatorConfig,
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

            Instance instance = new Instance(coordinatorInstanceId,
                    instanceType,
                    location,
                    null,
                    null);
            CoordinatorServer coordinatorServer = new CoordinatorServer(instance);
            instances.add(instance);
            if (autoStartInstances) {
                try {
                    coordinatorServer.start();
                }
                catch (Exception e) {
                    throw Throwables.propagate(e);
                }
            }
            coordinators.put(coordinatorInstanceId, coordinatorServer);
        }

        return ImmutableList.copyOf(instances);
    }

    public void terminateCoordinators(Iterable<String> instanceIds)
    {
        for (Entry<String, CoordinatorServer> entry : coordinators.entrySet()) {
            entry.getValue().destroy();
            coordinators.remove(entry.getKey());
        }
    }

    public void clearCoordinators()
    {
        terminateCoordinators(coordinators.keySet());
    }

    public ImmutableList<CoordinatorServer> getAllCoordinators()
    {
        return ImmutableList.copyOf(coordinators.values());
    }

    public CoordinatorServer getCoordinator(String instanceId)
    {
        return coordinators.get(instanceId);
    }

    public static class CoordinatorServer
    {
        private Instance instance;
        private final File resourcesFile;
        private final File tempDir;
        private TestingHttpServer coordinatorServer;
        private Coordinator coordinator;

        public CoordinatorServer(Instance instance)
        {
            Preconditions.checkNotNull(instance, "instance is null");
            this.instance = instance;

            tempDir = createTempDir("coordinator");
            resourcesFile = new File(tempDir, "slots/airship-resources.properties");
        }

        public void start()
                throws Exception
        {
            Map<String, String> coordinatorProperties = ImmutableMap.<String, String>builder()
                    .put("airship.version", "123")
                    .put("node.environment", "test")
                    .put("node.id", instance.getInstanceId())
                    .put("node.location", instance.getLocation())
                    .put("coordinator.slots-dir", new File(tempDir, "slots").getAbsolutePath())
                    .put("coordinator.resources-file", resourcesFile.getAbsolutePath())
                    .put("coordinator.binary-repo", "http://localhost:9999/")
                    .put("coordinator.default-group-id", "prod")
                    .put("coordinator.agent.default-config", "@agent.config")
                    .build();

            Injector coordinatorInjector = Guice.createInjector(new NodeModule(),
                    new TestingHttpServerModule(),
                    new TestingDiscoveryModule(),
                    new JsonModule(),
                    new JaxrsModule(),
                    new NullEventModule(),
                    new CoordinatorMainModule(),
                    new StaticProvisionerModule(),
                    new ConfigurationModule(new ConfigurationFactory(coordinatorProperties)));

            coordinatorServer = coordinatorInjector.getInstance(TestingHttpServer.class);
            coordinator = coordinatorInjector.getInstance(Coordinator.class);
            coordinatorServer.start();

            // update instance URIs to new http server
            instance = new Instance(instance.getInstanceId(),
                    instance.getInstanceType(),
                    instance.getLocation(),
                    coordinatorServer.getBaseUrl(),
                    coordinatorServer.getBaseUrl());
        }

        public void stop()
        {
            if (coordinatorServer != null) {
                try {
                    coordinatorServer.stop();
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

        public TestingHttpServer getCoordinatorServer()
        {
            return coordinatorServer;
        }

        public Coordinator getCoordinator()
        {
            return coordinator;
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

    private static class GetInstanceFunction implements Function<CoordinatorServer, Instance>
    {
        @Override
        public Instance apply(CoordinatorServer coordinatorServer)
        {
            return coordinatorServer.getInstance();
        }
    }

    @Override
    public List<Instance> listAgents()
    {
        return ImmutableList.copyOf(Iterables.transform(agents.values(), new GetAgentInstanceFunction()));
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

            Instance instance = new Instance(agentInstanceId,
                    instanceType,
                    location,
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
            resourcesFile = new File(tempDir, "slots/airship-resources.properties");
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

    private static class GetAgentInstanceFunction implements Function<AgentServer, Instance>
    {
        @Override
        public Instance apply(AgentServer agentServer)
        {
            return agentServer.getInstance();
        }
    }
}
