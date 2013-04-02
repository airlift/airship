package io.airlift.airship.integration;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ListMultimap;
import com.google.inject.Binder;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;
import com.google.inject.util.Modules;
import io.airlift.airship.cli.Airship;
import io.airlift.airship.cli.Airship.AirshipCommand;
import io.airlift.airship.cli.Config;
import io.airlift.airship.cli.InteractiveUser;
import io.airlift.airship.cli.OutputFormat;
import io.airlift.airship.coordinator.Coordinator;
import io.airlift.airship.coordinator.CoordinatorMainModule;
import io.airlift.airship.coordinator.InMemoryStateManager;
import io.airlift.airship.coordinator.Provisioner;
import io.airlift.airship.coordinator.StateManager;
import io.airlift.airship.coordinator.StaticProvisionerModule;
import io.airlift.airship.coordinator.TestingMavenRepository;
import io.airlift.airship.shared.AgentStatusRepresentation;
import io.airlift.airship.shared.Assignment;
import io.airlift.airship.shared.CoordinatorStatusRepresentation;
import io.airlift.airship.shared.SlotLifecycleState;
import io.airlift.airship.shared.SlotStatusRepresentation;
import io.airlift.configuration.ConfigurationFactory;
import io.airlift.configuration.ConfigurationModule;
import io.airlift.event.client.EventModule;
import io.airlift.http.server.testing.TestingHttpServer;
import io.airlift.http.server.testing.TestingHttpServerModule;
import io.airlift.jaxrs.JaxrsModule;
import io.airlift.json.JsonModule;
import io.airlift.node.NodeModule;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.google.inject.Scopes.SINGLETON;
import static io.airlift.airship.shared.AssignmentHelper.APPLE_ASSIGNMENT;
import static io.airlift.airship.shared.AssignmentHelper.APPLE_ASSIGNMENT_2;
import static io.airlift.airship.shared.AssignmentHelper.BANANA_ASSIGNMENT;
import static io.airlift.airship.shared.AssignmentHelper.BANANA_ASSIGNMENT_EXACT;
import static io.airlift.airship.shared.FileUtils.createTempDir;
import static io.airlift.airship.shared.FileUtils.deleteRecursively;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

public class TestCliIntegration
{
    private TestingHttpServer coordinatorServer;
    private Coordinator coordinator;
    private InMemoryStateManager stateManager;
    private MockLocalProvisioner provisioner;

    private File binaryRepoDir;
    private File localBinaryRepoDir;
    private File expectedStateDir;
    private File serviceInventoryCacheDir;

    private Config config;
    private MockInteractiveUser interactiveUser;
    private MockOutputFormat outputFormat;

    @BeforeClass
    public void setUp()
            throws Exception
    {
        try {
            binaryRepoDir = TestingMavenRepository.createBinaryRepoDir();
        }
        catch (Exception e) {
            e.printStackTrace();
            throw e;
        }

        localBinaryRepoDir = createTempDir("localBinaryRepoDir");
        expectedStateDir = createTempDir("expected-state");
        serviceInventoryCacheDir = createTempDir("service-inventory-cache");

        Map<String, String> coordinatorProperties = ImmutableMap.<String, String>builder()
                .put("node.environment", "prod")
                .put("airship.version", "123")
                .put("coordinator.binary-repo", binaryRepoDir.toURI().toString())
                .put("coordinator.default-group-id", "prod")
                .put("coordinator.binary-repo.local", localBinaryRepoDir.toString())
                .put("coordinator.status.expiration", "1s")
                .put("coordinator.agent.default-config", "@agent.config")
                .put("coordinator.aws.access-key", "my-access-key")
                .put("coordinator.aws.secret-key", "my-secret-key")
                .put("coordinator.aws.agent.ami", "ami-0123abcd")
                .put("coordinator.aws.agent.keypair", "keypair")
                .put("coordinator.aws.agent.security-group", "default")
                .put("coordinator.aws.agent.default-instance-type", "t1.micro")
                .put("coordinator.expected-state.dir", expectedStateDir.getAbsolutePath())
                .put("coordinator.service-inventory.cache-dir", serviceInventoryCacheDir.getAbsolutePath())
                .build();

        Injector coordinatorInjector = Guice.createInjector(new TestingHttpServerModule(),
                new NodeModule(),
                new JsonModule(),
                new JaxrsModule(),
                new EventModule(),
                new CoordinatorMainModule(),
                Modules.override(new StaticProvisionerModule()).with(new Module()
                {
                    @Override
                    public void configure(Binder binder)
                    {
                        binder.bind(StateManager.class).to(InMemoryStateManager.class).in(SINGLETON);
                        binder.bind(Provisioner.class).to(MockLocalProvisioner.class).in(SINGLETON);
                    }
                }),
                new ConfigurationModule(new ConfigurationFactory(coordinatorProperties)));

        coordinatorServer = coordinatorInjector.getInstance(TestingHttpServer.class);
        coordinator = coordinatorInjector.getInstance(Coordinator.class);
        stateManager = (InMemoryStateManager) coordinatorInjector.getInstance(StateManager.class);
        provisioner = (MockLocalProvisioner) coordinatorInjector.getInstance(Provisioner.class);
        provisioner.autoStartInstances = true;

        coordinator.start();
        coordinatorServer.start();
    }

    @BeforeMethod
    public void resetState()
            throws Exception
    {
        provisioner.clearCoordinators();
        coordinator.updateAllCoordinatorsAndWait();
        assertEquals(coordinator.getCoordinators().size(), 1);

        provisioner.clearAgents();
        coordinator.updateAllAgentsAndWait();
        assertTrue(coordinator.getAgents().isEmpty());

        stateManager.clearAll();
        assertTrue(coordinator.getAllSlotStatus().isEmpty());

        config = new Config();
        interactiveUser = new MockInteractiveUser(true);
        outputFormat = new MockOutputFormat();
    }

    @AfterClass
    public void stopServer()
            throws Exception
    {
        provisioner.clearAgents();
        provisioner.clearCoordinators();

        if (coordinatorServer != null) {
            coordinatorServer.stop();
        }

        if (binaryRepoDir != null) {
            deleteRecursively(binaryRepoDir);
        }
        if (expectedStateDir != null) {
            deleteRecursively(expectedStateDir);
        }
        if (serviceInventoryCacheDir != null) {
            deleteRecursively(serviceInventoryCacheDir);
        }
        if (localBinaryRepoDir != null) {
            deleteRecursively(localBinaryRepoDir);
        }
    }

    @Test
    public void testLocalModeHappyPath()
            throws Exception
    {
        execute("environment", "add", "local", coordinatorServer.getBaseUrl().toASCIIString());

        execute("show");

        assertNotNull(outputFormat.slots);
        assertEquals(outputFormat.slots.size(), 0);
        assertNull(outputFormat.coordinators);
        assertNull(outputFormat.agents);

        execute("agent", "show");

        assertNull(outputFormat.slots);
        assertNull(outputFormat.slots);
        assertNull(outputFormat.coordinators);
        assertEquals(outputFormat.agents.size(), 0);

        execute("agent", "provision");

        assertNull(outputFormat.slots);
        assertNull(outputFormat.slots);
        assertNull(outputFormat.coordinators);
        assertEquals(outputFormat.agents.size(), 1);
        AgentStatusRepresentation agent = outputFormat.agents.get(0);

        execute("coordinator", "show");

        assertNull(outputFormat.slots);
        assertNull(outputFormat.slots);
        assertEquals(outputFormat.coordinators.size(), 1);
        assertNull(outputFormat.agents);

        execute("install", APPLE_ASSIGNMENT.getConfig(), APPLE_ASSIGNMENT.getBinary());

        assertNotNull(outputFormat.slots);
        assertEquals(outputFormat.slots.size(), 1);
        SlotStatusRepresentation slot = outputFormat.slots.get(0);
        UUID slotId = slot.getId();
        assertNotNull(slotId);
        assertSlotStatus(slot, slotId, APPLE_ASSIGNMENT, SlotLifecycleState.STOPPED, agent);

        assertNull(outputFormat.coordinators);
        assertEquals(outputFormat.agents.size(), 1);

        execute("start", "-c", APPLE_ASSIGNMENT.getConfig());

        assertNotNull(outputFormat.slots);
        assertEquals(outputFormat.slots.size(), 1);
        assertSlotStatus(outputFormat.slots.get(0), slotId, APPLE_ASSIGNMENT, SlotLifecycleState.RUNNING, agent);

        assertNull(outputFormat.coordinators);
        assertNull(outputFormat.agents);

        execute("reset-to-actual", "-c", APPLE_ASSIGNMENT.getConfig());

        assertNotNull(outputFormat.slots);
        assertEquals(outputFormat.slots.size(), 1);
        assertSlotStatus(outputFormat.slots.get(0), slotId, APPLE_ASSIGNMENT, SlotLifecycleState.RUNNING, agent);

        assertNull(outputFormat.coordinators);
        assertNull(outputFormat.agents);

        execute("stop", "-c", APPLE_ASSIGNMENT.getConfig());

        assertNotNull(outputFormat.slots);
        assertEquals(outputFormat.slots.size(), 1);
        assertSlotStatus(outputFormat.slots.get(0), slotId, APPLE_ASSIGNMENT, SlotLifecycleState.STOPPED, agent);

        assertNull(outputFormat.coordinators);
        assertNull(outputFormat.agents);

        execute("restart", "-c", APPLE_ASSIGNMENT.getConfig());

        assertNotNull(outputFormat.slots);
        assertEquals(outputFormat.slots.size(), 1);
        assertSlotStatus(outputFormat.slots.get(0), slotId, APPLE_ASSIGNMENT, SlotLifecycleState.RUNNING, agent);

        assertNull(outputFormat.coordinators);
        assertNull(outputFormat.agents);

        execute("upgrade", "-c", APPLE_ASSIGNMENT.getConfig(), "2.0", "@2.0");

        assertNotNull(outputFormat.slots);
        assertEquals(outputFormat.slots.size(), 1);
        assertSlotStatus(outputFormat.slots.get(0), slotId, APPLE_ASSIGNMENT_2, SlotLifecycleState.RUNNING, agent);

        assertNull(outputFormat.coordinators);
        assertNull(outputFormat.agents);

        execute("start", "-c", APPLE_ASSIGNMENT_2.getConfig());

        assertNotNull(outputFormat.slots);
        assertEquals(outputFormat.slots.size(), 1);
        assertSlotStatus(outputFormat.slots.get(0), slotId, APPLE_ASSIGNMENT_2, SlotLifecycleState.RUNNING, agent);

        assertNull(outputFormat.coordinators);
        assertNull(outputFormat.agents);

        execute("restart", "-c", APPLE_ASSIGNMENT_2.getConfig());

        assertNotNull(outputFormat.slots);
        assertEquals(outputFormat.slots.size(), 1);
        assertSlotStatus(outputFormat.slots.get(0), slotId, APPLE_ASSIGNMENT_2, SlotLifecycleState.RUNNING, agent);

        assertNull(outputFormat.coordinators);
        assertNull(outputFormat.agents);

        execute("terminate", "-c", APPLE_ASSIGNMENT_2.getConfig());

        assertNotNull(outputFormat.slots);
        assertEquals(outputFormat.slots.size(), 1);
        assertSlotStatus(outputFormat.slots.get(0), slotId, APPLE_ASSIGNMENT_2, SlotLifecycleState.RUNNING, agent);

        assertNull(outputFormat.coordinators);
        assertNull(outputFormat.agents);

        execute("stop", "-c", APPLE_ASSIGNMENT_2.getConfig());

        assertNotNull(outputFormat.slots);
        assertEquals(outputFormat.slots.size(), 1);
        assertSlotStatus(outputFormat.slots.get(0), slotId, APPLE_ASSIGNMENT_2, SlotLifecycleState.STOPPED, agent);

        assertNull(outputFormat.coordinators);
        assertNull(outputFormat.agents);

        execute("terminate", "-c", APPLE_ASSIGNMENT_2.getConfig());

        assertNotNull(outputFormat.slots);
        assertEquals(outputFormat.slots.size(), 1);
        assertSlotStatus(outputFormat.slots.get(0), slotId, APPLE_ASSIGNMENT_2, SlotLifecycleState.TERMINATED, agent);

        assertNull(outputFormat.coordinators);
        assertNull(outputFormat.agents);

        execute("show");

        assertNotNull(outputFormat.slots);
        assertEquals(outputFormat.slots.size(), 0);
        assertNull(outputFormat.coordinators);
        assertNull(outputFormat.agents);
    }

    @Test
    public void testSlotFilter()
            throws Exception
    {
        execute("environment", "add", "local", coordinatorServer.getBaseUrl().toASCIIString());

        execute("agent", "provision");

        assertNull(outputFormat.slots);
        assertNull(outputFormat.slots);
        assertNull(outputFormat.coordinators);
        assertEquals(outputFormat.agents.size(), 1);

        execute("agent", "provision");

        assertNull(outputFormat.slots);
        assertNull(outputFormat.slots);
        assertNull(outputFormat.coordinators);
        assertEquals(outputFormat.agents.size(), 1);
        AgentStatusRepresentation agent = outputFormat.agents.get(0);

        execute("install", APPLE_ASSIGNMENT.getConfig(), APPLE_ASSIGNMENT.getBinary());
        assertNotNull(outputFormat.slots);
        assertEquals(outputFormat.slots.size(), 1);
        execute("install", APPLE_ASSIGNMENT.getConfig(), APPLE_ASSIGNMENT.getBinary());
        assertNotNull(outputFormat.slots);
        assertEquals(outputFormat.slots.size(), 1);
        execute("install", BANANA_ASSIGNMENT.getConfig(), BANANA_ASSIGNMENT.getBinary());
        assertNotNull(outputFormat.slots);
        assertEquals(outputFormat.slots.size(), 1);
        execute("install", BANANA_ASSIGNMENT.getConfig(), BANANA_ASSIGNMENT.getBinary());
        assertNotNull(outputFormat.slots);
        assertEquals(outputFormat.slots.size(), 1);

        ListMultimap<Assignment, SlotStatusRepresentation> slots;
        execute("show");
        slots = slotsByAssignment();
        assertEquals(slots.get(APPLE_ASSIGNMENT).size(), 2);
        assertEquals(slots.get(BANANA_ASSIGNMENT_EXACT).size(), 2);

        String appleSlotId = slots.get(APPLE_ASSIGNMENT).get(0).getId().toString();
        String appleShortSlotId = slots.get(APPLE_ASSIGNMENT).get(0).getShortId();
        String bananaSlotId = slots.get(BANANA_ASSIGNMENT_EXACT).get(0).getId().toString();
        String bananaShortSlotId = slots.get(BANANA_ASSIGNMENT_EXACT).get(0).getShortId();

        execute("show", "--all");
        slots = slotsByAssignment();
        assertEquals(slots.get(APPLE_ASSIGNMENT).size(), 2);
        assertEquals(slots.get(BANANA_ASSIGNMENT_EXACT).size(), 2);

        execute("show", "-u", appleSlotId);
        slots = slotsByAssignment();
        assertEquals(slots.get(APPLE_ASSIGNMENT).size(), 1);
        assertEquals(slots.get(APPLE_ASSIGNMENT).get(0).getId().toString(), appleSlotId);
        assertEquals(slots.get(BANANA_ASSIGNMENT_EXACT).size(), 0);

        execute("show", "-u", appleShortSlotId, "-u", bananaShortSlotId);
        slots = slotsByAssignment();
        assertEquals(slots.get(APPLE_ASSIGNMENT).size(), 1);
        assertEquals(slots.get(APPLE_ASSIGNMENT).get(0).getId().toString(), appleSlotId);
        assertEquals(slots.get(BANANA_ASSIGNMENT_EXACT).size(), 1);
        assertEquals(slots.get(BANANA_ASSIGNMENT_EXACT).get(0).getId().toString(), bananaSlotId);

        execute("show", "-u", appleSlotId, "-u", bananaSlotId);
        slots = slotsByAssignment();
        assertEquals(slots.get(APPLE_ASSIGNMENT).size(), 1);
        assertEquals(slots.get(APPLE_ASSIGNMENT).get(0).getId().toString(), appleSlotId);
        assertEquals(slots.get(BANANA_ASSIGNMENT_EXACT).size(), 1);
        assertEquals(slots.get(BANANA_ASSIGNMENT_EXACT).get(0).getId().toString(), bananaSlotId);

        execute("show", "-c", APPLE_ASSIGNMENT.getConfig());
        slots = slotsByAssignment();
        assertEquals(slots.get(APPLE_ASSIGNMENT).size(), 2);
        assertEquals(slots.get(BANANA_ASSIGNMENT_EXACT).size(), 0);

        execute("show", "-b", APPLE_ASSIGNMENT.getBinary());
        slots = slotsByAssignment();
        assertEquals(slots.get(APPLE_ASSIGNMENT).size(), 2);
        assertEquals(slots.get(BANANA_ASSIGNMENT_EXACT).size(), 0);

        execute("show", "-h", agent.getInternalHost());
        slots = slotsByAssignment();
        assertEquals(slots.get(APPLE_ASSIGNMENT).size(), 2);
        assertEquals(slots.get(BANANA_ASSIGNMENT_EXACT).size(), 2);

        execute("show", "-h", agent.getInternalIp());
        slots = slotsByAssignment();
        assertEquals(slots.get(APPLE_ASSIGNMENT).size(), 2);
        assertEquals(slots.get(BANANA_ASSIGNMENT_EXACT).size(), 2);

        execute("show", "-h", agent.getExternalHost());
        slots = slotsByAssignment();
        assertEquals(slots.get(APPLE_ASSIGNMENT).size(), 2);
        assertEquals(slots.get(BANANA_ASSIGNMENT_EXACT).size(), 2);

        execute("show", "-m", agent.getInstanceId());
        slots = slotsByAssignment();
        assertEquals(slots.get(APPLE_ASSIGNMENT).size(), 1);
        assertEquals(slots.get(BANANA_ASSIGNMENT_EXACT).size(), 1);

        execute("start", "-b", BANANA_ASSIGNMENT_EXACT.getBinary());
        slots = slotsByAssignment();
        assertEquals(slots.get(APPLE_ASSIGNMENT).size(), 0);
        assertEquals(slots.get(BANANA_ASSIGNMENT_EXACT).size(), 2);

        execute("start", "-s", SlotLifecycleState.RUNNING.toString());
        slots = slotsByAssignment();
        assertEquals(slots.get(APPLE_ASSIGNMENT).size(), 0);
        assertEquals(slots.get(BANANA_ASSIGNMENT_EXACT).size(), 2);

        execute("start", "-s", SlotLifecycleState.STOPPED.toString());
        slots = slotsByAssignment();
        assertEquals(slots.get(APPLE_ASSIGNMENT).size(), 2);
        assertEquals(slots.get(BANANA_ASSIGNMENT_EXACT).size(), 0);
    }

    private ListMultimap<Assignment, SlotStatusRepresentation> slotsByAssignment()
    {
        assertNotNull(outputFormat.slots);
        ArrayListMultimap<Assignment, SlotStatusRepresentation> slotsByAssignment = ArrayListMultimap.create();
        for (SlotStatusRepresentation slot : outputFormat.slots) {
            slotsByAssignment.put(new Assignment(slot.getBinary(), slot.getConfig()), slot);
        }
        return slotsByAssignment;
    }

    private void assertSlotStatus(SlotStatusRepresentation slot,
            UUID expectedSlotId,
            Assignment expectedAssignment,
            SlotLifecycleState expectedState,
            AgentStatusRepresentation expectedAgent)
    {
        assertEquals(slot.getId(), expectedSlotId);
        if (expectedState != SlotLifecycleState.TERMINATED) {
            assertEquals(slot.getBinary(), expectedAssignment.getBinary());
            assertEquals(slot.getConfig(), expectedAssignment.getConfig());
            assertNotNull(slot.getInstallPath());
        }
        else {
            assertNull(slot.getBinary());
            assertNull(slot.getConfig());
            assertNull(slot.getInstallPath());
        }
        assertEquals(slot.getStatus(), expectedState.toString());
        assertEquals(slot.getInstanceId(), expectedAgent.getInstanceId());
        assertTrue(slot.getLocation().startsWith(expectedAgent.getLocation()));
        assertTrue(slot.getLocation().endsWith(slot.getShortLocation()));
        assertTrue(slot.getSelf().toASCIIString().startsWith(expectedAgent.getSelf().toASCIIString()));
        assertTrue(slot.getExternalUri().toASCIIString().startsWith(expectedAgent.getExternalUri().toASCIIString()));
    }

    private void execute(String... args)
            throws Exception
    {
        outputFormat.clear();

        AirshipCommand command = Airship.AIRSHIP_PARSER.parse(ImmutableList.<String>builder().add("--debug").add(args).build());
        command.config = config;
        if (command instanceof Airship.AirshipCommanderCommand) {
            Airship.AirshipCommanderCommand airshipCommanderCommand = (Airship.AirshipCommanderCommand) command;
            airshipCommanderCommand.execute("local", outputFormat, interactiveUser);
        }
        else {
            command.execute();
        }
    }

    private static class MockOutputFormat implements OutputFormat
    {
        private List<CoordinatorStatusRepresentation> coordinators;
        private List<AgentStatusRepresentation> agents;
        private List<SlotStatusRepresentation> slots;

        public void clear()
        {
            coordinators = null;
            agents = null;
            slots = null;
        }

        @Override
        public void displayCoordinators(Iterable<CoordinatorStatusRepresentation> coordinators)
        {
            this.coordinators = ImmutableList.copyOf(coordinators);
        }

        @Override
        public void displayAgents(Iterable<AgentStatusRepresentation> agents)
        {
            this.agents = ImmutableList.copyOf(agents);
        }

        @Override
        public void displaySlots(Iterable<SlotStatusRepresentation> slots)
        {
            this.slots = ImmutableList.copyOf(slots);
        }
    }

    private static class MockInteractiveUser implements InteractiveUser
    {
        private boolean answer;

        private MockInteractiveUser(boolean answer)
        {
            this.answer = answer;
        }

        @Override
        public boolean ask(String question, boolean defaultValue)
        {
            return answer;
        }
    }
}
