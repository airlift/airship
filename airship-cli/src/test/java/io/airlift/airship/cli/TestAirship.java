package io.airlift.airship.cli;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ListMultimap;
import io.airlift.airship.cli.Airship.AirshipCommand;
import io.airlift.airship.cli.Airship.AirshipCommanderCommand;
import io.airlift.airship.coordinator.TestingMavenRepository;
import io.airlift.airship.shared.AgentStatusRepresentation;
import io.airlift.airship.shared.Assignment;
import io.airlift.airship.shared.CoordinatorStatusRepresentation;
import io.airlift.airship.shared.FileUtils;
import io.airlift.airship.shared.SlotLifecycleState;
import io.airlift.airship.shared.SlotStatusRepresentation;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.File;
import java.util.List;
import java.util.UUID;

import static io.airlift.airship.shared.AssignmentHelper.APPLE_ASSIGNMENT;
import static io.airlift.airship.shared.AssignmentHelper.APPLE_ASSIGNMENT_2;
import static io.airlift.airship.shared.AssignmentHelper.BANANA_ASSIGNMENT;
import static io.airlift.airship.shared.AssignmentHelper.BANANA_ASSIGNMENT_EXACT;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

public class TestAirship
{
    private File tempDir;

    private Config config;
    private TestingMavenRepository repo;
    private MockInteractiveUser interactiveUser;
    private MockOutputFormat outputFormat;

    @BeforeMethod
    public void setUp()
            throws Exception
    {
        tempDir = FileUtils.createTempDir("airship");

        config = new Config();
        repo = new TestingMavenRepository();
        interactiveUser = new MockInteractiveUser(true);
        outputFormat = new MockOutputFormat();
    }

    @AfterMethod
    public void tearDown()
            throws Exception
    {
        FileUtils.deleteRecursively(tempDir);
        repo.destroy();
    }

    @Test
    public void testLocalModeHappyPath()
            throws Exception
    {
        File targetRepo = repo.getTargetRepo();
        execute("environment", "provision-local", "local", new File(tempDir, "env").getAbsolutePath(),
                "--name", "monkey",
                "--repository", targetRepo.toURI().toASCIIString()
        );

        execute("show");

        assertNotNull(outputFormat.slots);
        assertEquals(outputFormat.slots.size(), 0);
        assertNull(outputFormat.coordinators);
        assertNull(outputFormat.agents);

        execute("agent", "show");

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
        assertSlotStatus(outputFormat.slots.get(0), slotId, APPLE_ASSIGNMENT_2, SlotLifecycleState.STOPPED, agent);

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
        File targetRepo = repo.getTargetRepo();
        execute("environment", "provision-local", "local", new File(tempDir, "env").getAbsolutePath(),
                "--name", "monkey",
                "--repository", targetRepo.toURI().toASCIIString(),
                "--allow-duplicate-installations"
        );

        execute("agent", "show");

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
        execute("install", APPLE_ASSIGNMENT_2.getConfig(), APPLE_ASSIGNMENT_2.getBinary());
        assertNotNull(outputFormat.slots);
        assertEquals(outputFormat.slots.size(), 1);
        execute("install", APPLE_ASSIGNMENT_2.getConfig(), APPLE_ASSIGNMENT_2.getBinary());
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
        assertEquals(slots.get(APPLE_ASSIGNMENT_2).size(), 2);
        assertEquals(slots.get(BANANA_ASSIGNMENT_EXACT).size(), 2);

        String appleSlotId = slots.get(APPLE_ASSIGNMENT).get(0).getId().toString();
        String appleShortSlotId = slots.get(APPLE_ASSIGNMENT).get(0).getShortId();
        String apple2SlotId = slots.get(APPLE_ASSIGNMENT_2).get(0).getId().toString();
        String apple2ShortSlotId = slots.get(APPLE_ASSIGNMENT_2).get(0).getShortId();
        String bananaSlotId = slots.get(BANANA_ASSIGNMENT_EXACT).get(0).getId().toString();
        String bananaShortSlotId = slots.get(BANANA_ASSIGNMENT_EXACT).get(0).getShortId();

        execute("show", "--all");
        slots = slotsByAssignment();
        assertEquals(slots.get(APPLE_ASSIGNMENT).size(), 2);
        assertEquals(slots.get(APPLE_ASSIGNMENT_2).size(), 2);
        assertEquals(slots.get(BANANA_ASSIGNMENT_EXACT).size(), 2);

        execute("show", "-u", appleSlotId);
        slots = slotsByAssignment();
        assertEquals(slots.get(APPLE_ASSIGNMENT).size(), 1);
        assertEquals(slots.get(APPLE_ASSIGNMENT).get(0).getId().toString(), appleSlotId);
        assertEquals(slots.get(APPLE_ASSIGNMENT_2).size(), 0);
        assertEquals(slots.get(BANANA_ASSIGNMENT_EXACT).size(), 0);

        execute("show", "-u", appleShortSlotId, "-u", apple2ShortSlotId, "-u", bananaShortSlotId);
        slots = slotsByAssignment();
        assertEquals(slots.get(APPLE_ASSIGNMENT).size(), 1);
        assertEquals(slots.get(APPLE_ASSIGNMENT).get(0).getId().toString(), appleSlotId);
        assertEquals(slots.get(APPLE_ASSIGNMENT_2).size(), 1);
        assertEquals(slots.get(APPLE_ASSIGNMENT_2).get(0).getId().toString(), apple2SlotId);
        assertEquals(slots.get(BANANA_ASSIGNMENT_EXACT).size(), 1);
        assertEquals(slots.get(BANANA_ASSIGNMENT_EXACT).get(0).getId().toString(), bananaSlotId);

        execute("show", "-u", appleSlotId, "-u", apple2SlotId, "-u", bananaSlotId);
        slots = slotsByAssignment();
        assertEquals(slots.get(APPLE_ASSIGNMENT).size(), 1);
        assertEquals(slots.get(APPLE_ASSIGNMENT).get(0).getId().toString(), appleSlotId);
        assertEquals(slots.get(APPLE_ASSIGNMENT_2).size(), 1);
        assertEquals(slots.get(APPLE_ASSIGNMENT_2).get(0).getId().toString(), apple2SlotId);
        assertEquals(slots.get(BANANA_ASSIGNMENT_EXACT).size(), 1);
        assertEquals(slots.get(BANANA_ASSIGNMENT_EXACT).get(0).getId().toString(), bananaSlotId);

        execute("show", "-c", APPLE_ASSIGNMENT.getConfig());
        slots = slotsByAssignment();
        assertEquals(slots.get(APPLE_ASSIGNMENT).size(), 2);
        assertEquals(slots.get(APPLE_ASSIGNMENT_2).size(), 0);
        assertEquals(slots.get(BANANA_ASSIGNMENT_EXACT).size(), 0);

        execute("show", "-b", APPLE_ASSIGNMENT.getBinary());
        slots = slotsByAssignment();
        assertEquals(slots.get(APPLE_ASSIGNMENT).size(), 2);
        assertEquals(slots.get(APPLE_ASSIGNMENT_2).size(), 0);
        assertEquals(slots.get(BANANA_ASSIGNMENT_EXACT).size(), 0);

        execute("show", "-h", agent.getInternalHost());
        slots = slotsByAssignment();
        assertEquals(slots.get(APPLE_ASSIGNMENT).size(), 2);
        assertEquals(slots.get(APPLE_ASSIGNMENT_2).size(), 2);
        assertEquals(slots.get(BANANA_ASSIGNMENT_EXACT).size(), 2);

        execute("show", "-h", agent.getInternalIp());
        slots = slotsByAssignment();
        assertEquals(slots.get(APPLE_ASSIGNMENT).size(), 2);
        assertEquals(slots.get(APPLE_ASSIGNMENT_2).size(), 2);
        assertEquals(slots.get(BANANA_ASSIGNMENT_EXACT).size(), 2);

        execute("show", "-h", agent.getExternalHost());
        slots = slotsByAssignment();
        assertEquals(slots.get(APPLE_ASSIGNMENT).size(), 2);
        assertEquals(slots.get(APPLE_ASSIGNMENT_2).size(), 2);
        assertEquals(slots.get(BANANA_ASSIGNMENT_EXACT).size(), 2);

        execute("show", "-m", agent.getInstanceId());
        slots = slotsByAssignment();
        assertEquals(slots.get(APPLE_ASSIGNMENT).size(), 2);
        assertEquals(slots.get(APPLE_ASSIGNMENT_2).size(), 2);
        assertEquals(slots.get(BANANA_ASSIGNMENT_EXACT).size(), 2);

        execute("start", "-b", BANANA_ASSIGNMENT_EXACT.getBinary());
        slots = slotsByAssignment();
        assertEquals(slots.get(APPLE_ASSIGNMENT).size(), 0);
        assertEquals(slots.get(APPLE_ASSIGNMENT_2).size(), 0);
        assertEquals(slots.get(BANANA_ASSIGNMENT_EXACT).size(), 2);

        execute("start", "-s", SlotLifecycleState.RUNNING.toString());
        slots = slotsByAssignment();
        assertEquals(slots.get(APPLE_ASSIGNMENT).size(), 0);
        assertEquals(slots.get(APPLE_ASSIGNMENT_2).size(), 0);
        assertEquals(slots.get(BANANA_ASSIGNMENT_EXACT).size(), 2);

        execute("start", "-s", SlotLifecycleState.STOPPED.toString());
        slots = slotsByAssignment();
        assertEquals(slots.get(APPLE_ASSIGNMENT).size(), 2);
        assertEquals(slots.get(APPLE_ASSIGNMENT_2).size(), 2);
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
            assertTrue(slot.getInstallPath().startsWith(tempDir.getAbsolutePath()));
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
        if (command instanceof AirshipCommanderCommand) {
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
