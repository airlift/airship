package com.proofpoint.galaxy.standalone;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.io.NullOutputStream;
import com.proofpoint.galaxy.shared.Assignment;
import com.proofpoint.galaxy.shared.UpgradeVersions;
import com.proofpoint.log.Logging;
import com.proofpoint.log.LoggingConfiguration;
import org.iq80.cli.Arguments;
import org.iq80.cli.Command;
import org.iq80.cli.GitLikeCommandParser;
import org.iq80.cli.GitLikeCommandParser.Builder;
import org.iq80.cli.Option;
import org.iq80.cli.Options;
import org.iq80.cli.ParseException;
import org.iq80.cli.model.GlobalMetadata;

import java.io.IOException;
import java.io.PrintStream;
import java.net.URI;
import java.util.List;
import java.util.concurrent.Callable;

import static com.google.common.base.Objects.firstNonNull;
import static com.google.common.collect.Lists.newArrayList;
import static com.proofpoint.galaxy.shared.SlotLifecycleState.RESTARTING;
import static com.proofpoint.galaxy.shared.SlotLifecycleState.RUNNING;
import static com.proofpoint.galaxy.shared.SlotLifecycleState.STOPPED;
import static com.proofpoint.galaxy.standalone.Column.binary;
import static com.proofpoint.galaxy.standalone.Column.config;
import static com.proofpoint.galaxy.standalone.Column.instanceType;
import static com.proofpoint.galaxy.standalone.Column.ip;
import static com.proofpoint.galaxy.standalone.Column.location;
import static com.proofpoint.galaxy.standalone.Column.shortId;
import static com.proofpoint.galaxy.standalone.Column.status;
import static com.proofpoint.galaxy.standalone.Column.statusMessage;
import static org.iq80.cli.HelpCommand.help;

public class Galaxy
{
    public static void main(String[] args)
            throws Exception
    {
        Builder<GalaxyCommand> builder = GitLikeCommandParser.parser("galaxy", GalaxyCommand.class)
                .withDescription("cloud management system")
                .defaultCommand(HelpCommand.class)
                .addCommand(HelpCommand.class)
                .addCommand(ShowCommand.class)
                .addCommand(InstallCommand.class)
                .addCommand(UpgradeCommand.class)
                .addCommand(TerminateCommand.class)
                .addCommand(StartCommand.class)
                .addCommand(StopCommand.class)
                .addCommand(RestartCommand.class)
                .addCommand(SshCommand.class)
                .addCommand(ResetToActualCommand.class);

        builder.addGroup("agent")
                .withDescription("Manage agents")
                .defaultCommand(AgentShowCommand.class)
                .addCommand(AgentShowCommand.class)
                .addCommand(AgentAddCommand.class)
                .addCommand(AgentTerminateCommand.class);

        GitLikeCommandParser<GalaxyCommand> galaxyParser = builder.build();

        galaxyParser.parse(args).call();
    }

    public static abstract class GalaxyCommand implements Callable<Void>
    {
        @Options
        public GlobalOptions globalOptions = new GlobalOptions();

        @Override
        public Void call()
                throws Exception
        {
            initializeLogging();

            Preconditions.checkNotNull(globalOptions.coordinator, "globalOptions.coordinator is null");
            URI coordinatorUri = new URI(globalOptions.coordinator);

            Commander commander = new CommanderFactory()
                    .setEnvironment(globalOptions.environment)
                    .setCoordinatorUri(coordinatorUri)
                    .setBinaryRepositories(globalOptions.binaryRepository)
                    .setConfigRepositories(globalOptions.configRepository)
                    .build();

            try {
                execute(commander);
            }
            catch (Exception e) {
                if (globalOptions.debug) {
                    throw e;
                }
                else {
                    System.out.println(firstNonNull(e.getMessage(), "Unknown error"));
                }
            }

            return null;
        }

        private void initializeLogging()
                throws IOException
        {
            // unhook out and err while initializing logging or logger will print to them
            PrintStream out = System.out;
            PrintStream err = System.err;
            try {
                if (globalOptions.debug) {
                    Logging logging = new Logging();
                    logging.initialize(new LoggingConfiguration());
                }
                else {
                    System.setOut(new PrintStream(new NullOutputStream()));
                    System.setErr(new PrintStream(new NullOutputStream()));

                    Logging logging = new Logging();
                    logging.initialize(new LoggingConfiguration());
                    logging.disableConsole();
                }
            }
            finally {
                System.setOut(out);
                System.setErr(err);
            }
        }

        public abstract void execute(Commander commander)
                throws Exception;

        public void displaySlots(Iterable<Record> slots)
        {
            if (Iterables.isEmpty(slots)) {
                System.out.println("No slots match the provided filters.");
            }
            else {
                TablePrinter tablePrinter = new TablePrinter(shortId, ip, status, binary, config, statusMessage);
                tablePrinter.print(slots);
            }
        }

        public void displayAgents(Iterable<Record> agents)
        {
            if (Iterables.isEmpty(agents)) {
                System.out.println("No agents match the provided filters.");
            }
            else {
                TablePrinter tablePrinter = new TablePrinter(shortId, ip, status, instanceType, location);
                tablePrinter.print(agents);
            }
        }
    }

    @Command(name = "help", description = "Display help information about galaxy")
    public static class HelpCommand extends GalaxyCommand
    {
        @Options
        public GlobalMetadata global;

        @Arguments
        public List<String> command = newArrayList();

        @Override
        public Void call()
                throws Exception
        {
            help(global, command);
            return null;
        }

        @Override
        public void execute(Commander commander)
                throws Exception
        {
        }

        @Override
        public String toString()
        {
            final StringBuilder sb = new StringBuilder();
            sb.append("HelpCommand");
            sb.append("{global=").append(global);
            sb.append(", command=").append(command);
            sb.append('}');
            return sb.toString();
        }
    }

    @Command(name = "show", description = "Show state of all slots")
    public static class ShowCommand extends GalaxyCommand
    {
        @Options
        public final SlotFilter slotFilter = new SlotFilter();

        @Override
        public void execute(Commander commander)
        {
            List<Record> slots = commander.show(slotFilter);
            displaySlots(slots);
        }

        @Override
        public String toString()
        {
            final StringBuilder sb = new StringBuilder();
            sb.append("ShowCommand");
            sb.append("{slotFilter=").append(slotFilter);
            sb.append(", globalOptions=").append(globalOptions);
            sb.append('}');
            return sb.toString();
        }
    }

    @Command(name = "install", description = "Install software in a new slot")
    public static class InstallCommand extends GalaxyCommand
    {
        @Option(name = {"--count"}, description = "Number of instances to install")
        public int count = 1;

        @Options
        public final AgentFilter agentFilter = new AgentFilter();

        @Arguments(usage = "<groupId:artifactId[:packaging[:classifier]]:version> @<component:pools:version>",
                description = "The binary and @configuration to install.  The default packaging is tar.gz")
        public final List<String> assignment = Lists.newArrayList();

        @Override
        public void execute(Commander commander)
        {
            if (assignment.size() != 2) {
                throw new ParseException("You must specify a binary and config to install.");
            }
            String binary;
            String config;
            if (assignment.get(0).startsWith("@")) {
                config = assignment.get(0);
                binary = assignment.get(1);
            }
            else {
                binary = assignment.get(0);
                config = assignment.get(1);
            }

            Assignment assignment = new Assignment(binary, config);
            List<Record> slots = commander.install(agentFilter, count, assignment);
            displaySlots(slots);
        }

        @Override
        public String toString()
        {
            final StringBuilder sb = new StringBuilder();
            sb.append("InstallCommand");
            sb.append("{count=").append(count);
            sb.append(", agentFilter=").append(agentFilter);
            sb.append(", assignment=").append(assignment);
            sb.append(", globalOptions=").append(globalOptions);
            sb.append('}');
            return sb.toString();
        }
    }

    @Command(name = "upgrade", description = "Upgrade software in a slot")
    public static class UpgradeCommand extends GalaxyCommand
    {
        @Options
        public final SlotFilter slotFilter = new SlotFilter();

        @Arguments(usage = "[<binary-version>] [@<config-version>]",
                description = "Version of the binary and/or @configuration")
        public final List<String> versions = Lists.newArrayList();

        @Override
        public void execute(Commander commander)
        {
            if (versions.size() != 1 && versions.size() != 2) {
                throw new ParseException("You must specify a binary version or a config version for upgrade.");
            }

            String binaryVersion = null;
            String configVersion = null;
            if (versions.get(0).startsWith("@")) {
                configVersion = versions.get(0);
                if (versions.size() > 1) {
                    binaryVersion = versions.get(1);
                }
            }
            else {
                binaryVersion = versions.get(0);
                if (versions.size() > 1) {
                    configVersion = versions.get(1);
                }
            }

            UpgradeVersions upgradeVersions = new UpgradeVersions(binaryVersion, configVersion);
            List<Record> slots = commander.upgrade(slotFilter, upgradeVersions);
            displaySlots(slots);
        }

        @Override
        public String toString()
        {
            final StringBuilder sb = new StringBuilder();
            sb.append("UpgradeCommand");
            sb.append("{slotFilter=").append(slotFilter);
            sb.append(", versions=").append(versions);
            sb.append(", globalOptions=").append(globalOptions);
            sb.append('}');
            return sb.toString();
        }
    }

    @Command(name = "terminate", description = "Terminate (remove) a slot")
    public static class TerminateCommand extends GalaxyCommand
    {
        @Options
        public final SlotFilter slotFilter = new SlotFilter();

        @Override
        public void execute(Commander commander)
        {
            List<Record> slots = commander.terminate(slotFilter);
            displaySlots(slots);
        }

        @Override
        public String toString()
        {
            final StringBuilder sb = new StringBuilder();
            sb.append("TerminateCommand");
            sb.append("{slotFilter=").append(slotFilter);
            sb.append(", globalOptions=").append(globalOptions);
            sb.append('}');
            return sb.toString();
        }
    }

    @Command(name = "start", description = "Start a server")
    public static class StartCommand extends GalaxyCommand
    {
        @Options
        public final SlotFilter slotFilter = new SlotFilter();

        @Override
        public void execute(Commander commander)
        {
            List<Record> slots = commander.setState(slotFilter, RUNNING);
            displaySlots(slots);
        }
    }

    @Command(name = "stop", description = "Stop a server")
    public static class StopCommand extends GalaxyCommand
    {
        @Options
        public final SlotFilter slotFilter = new SlotFilter();

        @Override
        public void execute(Commander commander)
        {
            List<Record> slots = commander.setState(slotFilter, STOPPED);
            displaySlots(slots);
        }
    }

    @Command(name = "restart", description = "Restart server")
    public static class RestartCommand extends GalaxyCommand
    {
        @Options
        public final SlotFilter slotFilter = new SlotFilter();

        @Override
        public void execute(Commander commander)
        {
            List<Record> slots = commander.setState(slotFilter, RESTARTING);
            displaySlots(slots);
        }
    }

    @Command(name = "reset-to-actual", description = "Reset slot expected state to actual")
    public static class ResetToActualCommand extends GalaxyCommand
    {
        @Options
        public final SlotFilter slotFilter = new SlotFilter();

        @Override
        public void execute(Commander commander)
        {
            List<Record> slots = commander.resetExpectedState(slotFilter);
            displaySlots(slots);
        }
    }

    @Command(name = "ssh", description = "ssh to slot installation")
    public static class SshCommand extends GalaxyCommand
    {
        @Options
        public final SlotFilter slotFilter = new SlotFilter();

        @Arguments(description = "Command to execute on the remote host")
        public String command;

        @Override
        public void execute(Commander commander)
        {
            commander.ssh(slotFilter, command);
        }

        @Override
        public String toString()
        {
            final StringBuilder sb = new StringBuilder();
            sb.append("InstallCommand");
            sb.append("{slotFilter=").append(slotFilter);
            sb.append(", args=").append(command);
            sb.append(", globalOptions=").append(globalOptions);
            sb.append('}');
            return sb.toString();
        }
    }

    @Command(name = "show", description = "Show agent details")
    public static class AgentShowCommand extends GalaxyCommand
    {
        @Options
        public final AgentFilter agentFilter = new AgentFilter();

        @Override
        public void execute(Commander commander)
                throws Exception
        {
            List<Record> agents = commander.showAgents(agentFilter);
            displayAgents(agents);
        }

        @Override
        public String toString()
        {
            final StringBuilder sb = new StringBuilder();
            sb.append("AgentShowCommand");
            sb.append("{globalOptions=").append(globalOptions);
            sb.append(", agentFilter=").append(agentFilter);
            sb.append('}');
            return sb.toString();
        }
    }

    @Command(name = "add", description = "Provision a new agent")
    public static class AgentAddCommand extends GalaxyCommand
    {
        @Option(name = {"--count"}, description = "Number of agents to provision")
        public int count = 1;

        @Option(name = {"--availability-zone"}, description = "Availability zone to provision")
        public String availabilityZone;

        @Arguments(usage = "[<instance-type>]", description = "Instance type to provision")
        public String instanceType;

        @Override
        public void execute(Commander commander)
                throws Exception
        {
            List<Record> agents = commander.addAgents(count, availabilityZone, instanceType);
            displayAgents(agents);
        }

        @Override
        public String toString()
        {
            final StringBuilder sb = new StringBuilder();
            sb.append("AgentAddCommand");
            sb.append("{count=").append(count);
            sb.append(", availabilityZone='").append(availabilityZone).append('\'');
            sb.append(", instanceType=").append(instanceType);
            sb.append(", globalOptions=").append(globalOptions);
            sb.append('}');
            return sb.toString();
        }
    }

    @Command(name = "terminate", description = "Provision a new agent")
    public static class AgentTerminateCommand extends GalaxyCommand
    {
        @Arguments(title = "agent-id", description = "Agent to terminate", required = true)
        public String agentId;

        @Override
        public void execute(Commander commander)
                throws Exception
        {
            Record agent = commander.terminateAgent(agentId);
            displayAgents(ImmutableList.of(agent));
        }

        @Override
        public String toString()
        {
            final StringBuilder sb = new StringBuilder();
            sb.append("AgentTerminateCommand");
            sb.append("{agentId='").append(agentId).append('\'');
            sb.append(", globalOptions=").append(globalOptions);
            sb.append('}');
            return sb.toString();
        }
    }
}
