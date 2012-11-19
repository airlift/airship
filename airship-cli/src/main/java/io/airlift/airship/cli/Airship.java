package io.airlift.airship.cli;

import com.amazonaws.AmazonClientException;
import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.services.ec2.AmazonEC2Client;
import com.amazonaws.services.ec2.model.DescribeInstancesRequest;
import com.amazonaws.services.ec2.model.DescribeInstancesResult;
import com.amazonaws.services.ec2.model.Reservation;
import com.amazonaws.services.identitymanagement.AmazonIdentityManagementClient;
import com.amazonaws.services.identitymanagement.model.AccessKey;
import com.amazonaws.services.identitymanagement.model.CreateAccessKeyRequest;
import com.amazonaws.services.identitymanagement.model.CreateAccessKeyResult;
import com.amazonaws.services.identitymanagement.model.CreateUserRequest;
import com.amazonaws.services.identitymanagement.model.PutUserPolicyRequest;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.io.NullOutputStream;
import com.google.common.io.Resources;
import io.airlift.airship.cli.CommanderFactory.ToUriFunction;
import io.airlift.airship.coordinator.AwsProvisioner;
import io.airlift.airship.coordinator.AwsProvisionerConfig;
import io.airlift.airship.coordinator.CoordinatorConfig;
import io.airlift.airship.coordinator.HttpRepository;
import io.airlift.airship.coordinator.Instance;
import io.airlift.airship.coordinator.MavenRepository;
import io.airlift.airship.shared.AgentStatusRepresentation;
import io.airlift.airship.shared.Assignment;
import io.airlift.airship.shared.CoordinatorStatusRepresentation;
import io.airlift.airship.shared.Repository;
import io.airlift.airship.shared.RepositorySet;
import io.airlift.airship.shared.SlotStatusRepresentation;
import io.airlift.airship.shared.UpgradeVersions;
import io.airlift.command.Arguments;
import io.airlift.command.Cli;
import io.airlift.command.Cli.CliBuilder;
import io.airlift.command.Command;
import io.airlift.command.Help;
import io.airlift.command.Option;
import io.airlift.command.ParseException;
import io.airlift.configuration.ConfigurationFactory;
import io.airlift.http.server.HttpServerConfig;
import io.airlift.http.server.HttpServerInfo;
import io.airlift.json.JsonCodec;
import io.airlift.log.Logging;
import io.airlift.log.LoggingConfiguration;
import io.airlift.log.LoggingMBean;
import io.airlift.node.NodeInfo;

import javax.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.net.URI;
import java.net.URL;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.Callable;

import static com.google.common.base.Objects.firstNonNull;
import static com.google.common.collect.Lists.newArrayList;
import static io.airlift.airship.coordinator.AwsProvisioner.toInstance;
import static io.airlift.airship.shared.ConfigUtils.createConfigurationFactory;
import static io.airlift.airship.shared.HttpUriBuilder.uriBuilder;
import static io.airlift.airship.shared.SlotLifecycleState.RESTARTING;
import static io.airlift.airship.shared.SlotLifecycleState.RUNNING;
import static io.airlift.airship.shared.SlotLifecycleState.STOPPED;
import static java.lang.String.format;
import static java.util.UUID.randomUUID;

public class Airship
{
    private static final int EXIT_SUCCESS = 0;
    private static final int EXIT_FAILURE = 1;
    private static final int EXIT_PERMANENT = 100;
//    private static final int EXIT_TRANSIENT = 111;

    private static final File CONFIG_FILE = new File(System.getProperty("user.home", "."), ".airshipconfig");

    public static final Cli<AirshipCommand> AIRSHIP_PARSER;

    static {
        CliBuilder<AirshipCommand> builder =  Cli.<AirshipCommand>builder("airship")
                .withDescription("cloud management system")
                .withDefaultCommand(HelpCommand.class)
                .withCommands(HelpCommand.class,
                        ShowCommand.class,
                        InstallCommand.class,
                        UpgradeCommand.class,
                        TerminateCommand.class,
                        StartCommand.class,
                        StopCommand.class,
                        RestartCommand.class,
                        SshCommand.class,
                        ResetToActualCommand.class);

        builder.withGroup("coordinator")
                .withDescription("Manage coordinators")
                .withDefaultCommand(CoordinatorShowCommand.class)
                .withCommands(CoordinatorShowCommand.class,
                        CoordinatorProvisionCommand.class,
                        CoordinatorSshCommand.class);

        builder.withGroup("agent")
                .withDescription("Manage agents")
                .withDefaultCommand(AgentShowCommand.class)
                .withCommands(AgentShowCommand.class,
                        AgentProvisionCommand.class,
                        AgentTerminateCommand.class,
                        AgentSshCommand.class);

        builder.withGroup("environment")
                .withDescription("Manage environments")
                .withDefaultCommand(EnvironmentShow.class)
                .withCommands(EnvironmentShow.class,
                        EnvironmentProvisionLocal.class,
                        EnvironmentProvisionAws.class,
                        EnvironmentUse.class,
                        EnvironmentAdd.class,
                        EnvironmentRemove.class);

        builder.withGroup("config")
                .withDescription("Manage configuration")
                .withDefaultCommand(HelpCommand.class)
                .withCommands(ConfigGet.class,
                        ConfigGetAll.class,
                        ConfigSet.class,
                        ConfigAdd.class,
                        ConfigUnset.class);

        AIRSHIP_PARSER = builder.build();
    }

    public static void main(String[] args)
            throws Exception
    {
        try {
            System.exit(AIRSHIP_PARSER.parse(args).call());
        }
        catch (ParseException e) {
            System.out.println(firstNonNull(e.getMessage(), "Unknown command line parser error"));
            System.exit(EXIT_PERMANENT);
        }
    }

    public static abstract class AirshipCommand
            implements Callable<Integer>
    {
        @Inject
        public GlobalOptions globalOptions = new GlobalOptions();

        @VisibleForTesting
        public Config config;

        @Override
        public final Integer call()
                throws Exception
        {
            initializeLogging(globalOptions.debug);

            config = Config.loadConfig(CONFIG_FILE);

            try {
                execute();
            }
            catch (Exception e) {
                if (globalOptions.debug) {
                    throw e;
                }
                else {
                    System.out.println(firstNonNull(e.getMessage(), "Unknown error"));
                    return EXIT_FAILURE;
                }
            }
            return EXIT_SUCCESS;
        }

        @VisibleForTesting
        public abstract void execute() throws Exception;
    }

    public static abstract class AirshipCommanderCommand
            extends AirshipCommand
    {
        protected String environmentRef;
        protected OutputFormat outputFormat;
        protected InteractiveUser interactiveUser;

        @Override
        public void execute()
                throws Exception
        {
            String environmentRef = globalOptions.environment;
            if (environmentRef == null) {
                environmentRef = config.get("environment.default");
            }
            if (environmentRef == null) {
                throw new RuntimeException("You must specify an environment.");
            }

            OutputFormat outputFormat = new TableOutputFormat(environmentRef, config);

            InteractiveUser interactiveUser = new RealInteractiveUser();

            execute(environmentRef, outputFormat, interactiveUser);
        }

        @VisibleForTesting
        public void execute(String environmentRef, OutputFormat outputFormat, InteractiveUser interactiveUser)
                throws Exception
        {
            this.environmentRef = environmentRef;
            this.outputFormat = outputFormat;
            this.interactiveUser = interactiveUser;

            String environment = config.get("environment." + environmentRef + ".name");
            if (environment == null) {
                throw new RuntimeException("Unknown environment " + environmentRef);
            }
            String coordinator = config.get("environment." + environmentRef + ".coordinator");
            if (coordinator == null) {
                throw new RuntimeException("Environment " + environmentRef + " does not have a coordinator url.  You can add a coordinator url with airship coordinator add <url>");
            }

            URI coordinatorUri = new URI(coordinator);

            CommanderFactory commanderFactory = new CommanderFactory()
                    .setEnvironment(environment)
                    .setCoordinatorUri(coordinatorUri)
                    .setRepositories(config.getAll("environment." + environmentRef + ".repository"))
                    .setMavenDefaultGroupIds(config.getAll("environment." + environmentRef + ".maven-group-id"));

            if (config.get("environment." + environmentRef + ".coordinator-id") != null) {
                commanderFactory.setCoordinatorId(config.get("environment." + environmentRef + ".coordinator-id"));
            }
            if (config.get("environment." + environmentRef + ".agent-id") != null) {
                commanderFactory.setAgentId(config.get("environment." + environmentRef + ".agent-id"));
            }
            if (config.get("environment." + environmentRef + ".location") != null) {
                commanderFactory.setLocation(config.get("environment." + environmentRef + ".location"));
            }
            if (config.get("environment." + environmentRef + ".instance-type") != null) {
                commanderFactory.setInstanceType(config.get("environment." + environmentRef + ".instance-type"));
            }
            if (config.get("environment." + environmentRef + ".internal-ip") != null) {
                commanderFactory.setInternalIp(config.get("environment." + environmentRef + ".internal-ip"));
            }
            if (config.get("environment." + environmentRef + ".external-address") != null) {
                commanderFactory.setExternalAddress(config.get("environment." + environmentRef + ".external-address"));
            }
            if ("true".equalsIgnoreCase(config.get("environment." + environmentRef + ".use-internal-address"))) {
                commanderFactory.setUseInternalAddress(true);
            }

            Commander commander = commanderFactory.build();

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
        }

        public abstract void execute(Commander commander)
                throws Exception;


        public boolean ask(String question, boolean defaultValue)
        {
            return interactiveUser.ask(question, defaultValue);
        }

        public void verifySlotExecution(Commander commander, SlotFilter slotFilter, String question, boolean defaultValue, SlotExecution slotExecution)
        {
            Preconditions.checkArgument(slotFilter.isFiltered(), "A filter is required");

            if (globalOptions.batch) {
                slotExecution.execute(commander, slotFilter, null);
                return;
            }

            // show effected slots
            CommanderResponse<List<SlotStatusRepresentation>> response = commander.show(slotFilter);
            displaySlots(response.getValue());
            if (response.getValue().isEmpty()) {
                return;
            }
            System.out.println();

            // ask to continue
            if (!ask(question, defaultValue)) {
                return;
            }

            // return filter for only the shown slots
            SlotFilter uuidFilter = new SlotFilter();
            for (SlotStatusRepresentation slot : response.getValue()) {
                uuidFilter.uuid.add(slot.getId().toString());
            }
            slotExecution.execute(commander, uuidFilter, response.getVersion());
        }

        public void displaySlots(Iterable<SlotStatusRepresentation> slots)
        {
            outputFormat.displaySlots(slots);
        }

        public void displayAgents(Iterable<AgentStatusRepresentation> agents)
        {
            outputFormat.displayAgents(agents);
        }

        public void displayCoordinators(Iterable<CoordinatorStatusRepresentation> coordinators)
        {
            outputFormat.displayCoordinators(coordinators);
        }

        protected interface SlotExecution {
            void execute(Commander commander, SlotFilter slotFilter, String expectedVersion);
        }

    }

    @Command(name = "help", description = "Display help information about airship")
    public static class HelpCommand extends AirshipCommand
    {
        @Inject
        public Help help;

        @Override
        public void execute()
                throws Exception
        {
            help.call();
        }

        @Override
        public String toString()
        {
            final StringBuilder sb = new StringBuilder();
            sb.append("HelpCommand");
            sb.append("{help=").append(help);
            sb.append('}');
            return sb.toString();
        }
    }

    @Command(name = "show", description = "Show state of all slots")
    public static class ShowCommand extends AirshipCommanderCommand
    {
        @Inject
        public final SlotFilter slotFilter = new SlotFilter();

        @Override
        public void execute(Commander commander)
        {
            CommanderResponse<List<SlotStatusRepresentation>> response = commander.show(slotFilter);
            displaySlots(response.getValue());
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
    public static class InstallCommand extends AirshipCommanderCommand
    {
        @Option(name = {"--count"}, description = "Number of instances to install")
        public int count = 1;

        @Inject
        public final AgentFilter agentFilter = new AgentFilter();

        @Arguments(usage = "<groupId:artifactId[:packaging[:classifier]]:version> @<component:pools:version>",
                description = "The binary and @configuration to install.  The default packaging is tar.gz")
        public final List<String> assignment = Lists.newArrayList();

        @Override
        public void execute(Commander commander)
        {
            if (assignment.size() != 2) {
                throw new ParseException("You must specify a binary and @config to install.");
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

            if (!config.startsWith("@")) {
                throw new ParseException("Configuration specification must start with an at sign (@).");
            }

            Assignment assignment = new Assignment(binary, config);

            // add assignment to agent filter
            agentFilter.assignableFilters.add(assignment);

            if (globalOptions.batch) {
                List<SlotStatusRepresentation> slots = commander.install(agentFilter, count, assignment, null);
                displaySlots(slots);
                return;
            }

            // select agents
            CommanderResponse<List<AgentStatusRepresentation>> response = commander.showAgents(agentFilter);
            List<AgentStatusRepresentation> agents = response.getValue();
            if (agents.isEmpty()) {
                System.out.println("No agents match the provided filters, matched the software constrains or had the required resources available for the software");
                return;
            }

            // limit count
            if (agents.size() > count) {
                agents = newArrayList(agents);
                Collections.shuffle(agents);
                agents = agents.subList(0, count);
            }

            // show effected agents
            displayAgents(agents);
            System.out.println();

            // ask to continue
            if (!ask("Are you sure you would like to INSTALL on these agents?", true)) {
                return;
            }

            // build filter for only the shown agents
            AgentFilter uuidFilter = new AgentFilter();
            for (AgentStatusRepresentation agent : agents) {
                uuidFilter.uuid.add(agent.getAgentId());
            }

            // install software
            List<SlotStatusRepresentation> slots = commander.install(uuidFilter, count, assignment, response.getVersion());
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
    public static class UpgradeCommand extends AirshipCommanderCommand
    {
        @Inject
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

            final UpgradeVersions upgradeVersions = new UpgradeVersions(binaryVersion, configVersion);

            verifySlotExecution(commander, slotFilter, "Are you sure you would like to UPGRADE these servers?", false, new SlotExecution()
            {
                public void execute(Commander commander, SlotFilter slotFilter, String expectedVersion)
                {
                    List<SlotStatusRepresentation> slots = commander.upgrade(slotFilter, upgradeVersions, expectedVersion);
                    displaySlots(slots);
                }
            });
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
    public static class TerminateCommand extends AirshipCommanderCommand
    {
        @Inject
        public final SlotFilter slotFilter = new SlotFilter();

        @Override
        public void execute(Commander commander)
        {
            verifySlotExecution(commander, slotFilter, "Are you sure you would like to TERMINATE these servers?", false, new SlotExecution()
            {
                public void execute(Commander commander, SlotFilter slotFilter, String expectedVersion)
                {
                    List<SlotStatusRepresentation> slots = commander.terminate(slotFilter, expectedVersion);
                    displaySlots(slots);
                }
            });
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
    public static class StartCommand extends AirshipCommanderCommand
    {
        @Inject
        public final SlotFilter slotFilter = new SlotFilter();

        @Override
        public void execute(Commander commander)
        {
            verifySlotExecution(commander, slotFilter, "Are you sure you would like to START these servers?", true, new SlotExecution()
            {
                public void execute(Commander commander, SlotFilter slotFilter, String expectedVersion)
                {
                    List<SlotStatusRepresentation> slots = commander.setState(slotFilter, RUNNING, expectedVersion);
                    displaySlots(slots);
                }
            });
        }
    }

    @Command(name = "stop", description = "Stop a server")
    public static class StopCommand extends AirshipCommanderCommand
    {
        @Inject
        public final SlotFilter slotFilter = new SlotFilter();

        @Override
        public void execute(Commander commander)
        {
            verifySlotExecution(commander, slotFilter, "Are you sure you would like to STOP these servers?", true, new SlotExecution()
            {
                public void execute(Commander commander, SlotFilter slotFilter, String expectedVersion)
                {
                    List<SlotStatusRepresentation> slots = commander.setState(slotFilter, STOPPED, expectedVersion);
                    displaySlots(slots);
                }
            });
        }
    }

    @Command(name = "restart", description = "Restart server")
    public static class RestartCommand extends AirshipCommanderCommand
    {
        @Inject
        public final SlotFilter slotFilter = new SlotFilter();

        @Override
        public void execute(Commander commander)
        {
            verifySlotExecution(commander, slotFilter, "Are you sure you would like to RESTART these servers?", true, new SlotExecution()
            {
                public void execute(Commander commander, SlotFilter slotFilter, String expectedVersion)
                {
                    List<SlotStatusRepresentation> slots = commander.setState(slotFilter, RESTARTING, expectedVersion);
                    displaySlots(slots);
                }
            });
        }
    }

    @Command(name = "reset-to-actual", description = "Reset slot expected state to actual")
    public static class ResetToActualCommand extends AirshipCommanderCommand
    {
        @Inject
        public final SlotFilter slotFilter = new SlotFilter();

        @Override
        public void execute(Commander commander)
        {
            verifySlotExecution(commander, slotFilter, "Are you sure you would like to reset these servers to their actual state?", true, new SlotExecution()
            {
                public void execute(Commander commander, SlotFilter slotFilter, String expectedVersion)
                {
                    List<SlotStatusRepresentation> slots = commander.resetExpectedState(slotFilter, expectedVersion);
                    displaySlots(slots);
                }
            });
        }
    }

    @Command(name = "ssh", description = "ssh to slot installation")
    public static class SshCommand extends AirshipCommanderCommand
    {
        @Inject
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

    @Command(name = "show", description = "Show coordinator details")
    public static class CoordinatorShowCommand extends AirshipCommanderCommand
    {
        @Inject
        public final CoordinatorFilter coordinatorFilter = new CoordinatorFilter();

        @Override
        public void execute(Commander commander)
                throws Exception
        {
            List<CoordinatorStatusRepresentation> coordinators = commander.showCoordinators(coordinatorFilter);
            displayCoordinators(coordinators);
        }

        @Override
        public String toString()
        {
            final StringBuilder sb = new StringBuilder();
            sb.append("CoordinatorShowCommand");
            sb.append("{globalOptions=").append(globalOptions);
            sb.append(", coordinatorFilter=").append(coordinatorFilter);
            sb.append('}');
            return sb.toString();
        }
    }

    @Command(name = "provision", description = "Provision a new coordinator")
    public static class CoordinatorProvisionCommand extends AirshipCommanderCommand
    {
        @Option(name = "--coordinator-config", description = "Configuration for the coordinator")
        public String coordinatorConfig;

        @Option(name = {"--count"}, description = "Number of coordinators to provision")
        public int count = 1;

        @Option(name = "--ami", description = "Amazon Machine Image for coordinator")
        public String ami = "ami-27b7744e";

        @Option(name = "--key-pair", description = "Key pair for coordinator")
        public String keyPair = "keypair";

        @Option(name = "--security-group", description = "Security group for coordinator")
        public String securityGroup = "default";

        @Option(name = "--availability-zone", description = "EC2 availability zone for coordinator")
        public String availabilityZone;

        @Option(name = "--instance-type", description = "Instance type to provision")
        public String instanceType = "t1.micro";

        @Option(name = "--no-wait", description = "Do not wait for coordinator to start")
        public boolean noWait;

        @Override
        public void execute(Commander commander)
                throws Exception
        {
            List<CoordinatorStatusRepresentation> coordinators = commander.provisionCoordinators(coordinatorConfig,
                    count,
                    instanceType,
                    availabilityZone,
                    ami,
                    keyPair,
                    securityGroup,
                    !noWait);

            // add the new coordinators to the config
            String coordinatorProperty = "environment." + environmentRef + ".coordinator";
            for (CoordinatorStatusRepresentation coordinator : coordinators) {
                URI uri = coordinator.getExternalUri();
                if (uri != null) {
                    config.add(coordinatorProperty, uri.toASCIIString());
                }
            }
            config.save();

            displayCoordinators(coordinators);
        }

        @Override
        public String toString()
        {
            final StringBuilder sb = new StringBuilder();
            sb.append("CoordinatorProvisionCommand");
            sb.append("{coordinatorConfig='").append(coordinatorConfig).append('\'');
            sb.append(", count=").append(count);
            sb.append(", ami='").append(ami).append('\'');
            sb.append(", keyPair='").append(keyPair).append('\'');
            sb.append(", securityGroup='").append(securityGroup).append('\'');
            sb.append(", availabilityZone='").append(availabilityZone).append('\'');
            sb.append(", instanceType='").append(instanceType).append('\'');
            sb.append('}');
            return sb.toString();
        }
    }

    @Command(name = "ssh", description = "ssh to coordinator host")
    public static class CoordinatorSshCommand extends AirshipCommanderCommand
    {
        @Inject
        public final CoordinatorFilter coordinatorFilter = new CoordinatorFilter();

        @Arguments(description = "Command to execute on the remote host")
        public String command;

        @Override
        public void execute(Commander commander)
        {
            commander.sshCoordinator(coordinatorFilter, command);
        }

        @Override
        public String toString()
        {
            final StringBuilder sb = new StringBuilder();
            sb.append("CoordinatorSshCommand");
            sb.append("{coordinatorFilter=").append(coordinatorFilter);
            sb.append(", command='").append(command).append('\'');
            sb.append('}');
            return sb.toString();
        }
    }

    @Command(name = "show", description = "Show agent details")
    public static class AgentShowCommand extends AirshipCommanderCommand
    {
        @Inject
        public final AgentFilter agentFilter = new AgentFilter();

        @Override
        public void execute(Commander commander)
                throws Exception
        {
            CommanderResponse<List<AgentStatusRepresentation>> response = commander.showAgents(agentFilter);
            displayAgents(response.getValue());
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

    @Command(name = "provision", description = "Provision a new agent")
    public static class AgentProvisionCommand extends AirshipCommanderCommand
    {
        @Option(name = "--agent-config", description = "Agent for the coordinator")
        public String agentConfig;

        @Option(name = {"--count"}, description = "Number of agent to provision")
        public int count = 1;

        @Option(name = "--ami", description = "Amazon Machine Image for agent")
        public String ami = "ami-27b7744e";

        @Option(name = "--key-pair", description = "Key pair for agent")
        public String keyPair = "keypair";

        @Option(name = "--security-group", description = "Security group for agent")
        public String securityGroup = "default";

        @Option(name = "--availability-zone", description = "EC2 availability zone for agent")
        public String availabilityZone;

        @Option(name = "--instance-type", description = "Instance type to provision")
        public String instanceType = "t1.micro";

        @Option(name = "--no-wait", description = "Do not wait for agent to start")
        public boolean noWait;

        @Override
        public void execute(Commander commander)
                throws Exception
        {
            List<AgentStatusRepresentation> agents = commander.provisionAgents(agentConfig, count, instanceType, availabilityZone, ami, keyPair, securityGroup, !noWait);
            displayAgents(agents);
        }

        @Override
        public String toString()
        {
            final StringBuilder sb = new StringBuilder();
            sb.append("AgentProvisionCommand");
            sb.append("{agentConfig='").append(agentConfig).append('\'');
            sb.append(", count=").append(count);
            sb.append(", ami='").append(ami).append('\'');
            sb.append(", keyPair='").append(keyPair).append('\'');
            sb.append(", securityGroup='").append(securityGroup).append('\'');
            sb.append(", availabilityZone='").append(availabilityZone).append('\'');
            sb.append(", instanceType='").append(instanceType).append('\'');
            sb.append(", noWait=").append(noWait);
            sb.append('}');
            return sb.toString();
        }
    }

    @Command(name = "terminate", description = "Terminate an agent")
    public static class AgentTerminateCommand extends AirshipCommanderCommand
    {
        @Arguments(title = "agent-id", description = "Agent to terminate", required = true)
        public String agentId;

        @Override
        public void execute(Commander commander)
                throws Exception
        {
            AgentStatusRepresentation agent = commander.terminateAgent(agentId);
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

    @Command(name = "ssh", description = "ssh to agent host")
    public static class AgentSshCommand extends AirshipCommanderCommand
    {
        @Inject
        public final AgentFilter agentFilter = new AgentFilter();

        @Arguments(description = "Command to execute on the remote host")
        public String command;

        @Override
        public void execute(Commander commander)
        {
            commander.sshAgent(agentFilter, command);
        }

        @Override
        public String toString()
        {
            final StringBuilder sb = new StringBuilder();
            sb.append("AgentSshCommand");
            sb.append("{agentFilter=").append(agentFilter);
            sb.append(", command='").append(command).append('\'');
            sb.append('}');
            return sb.toString();
        }
    }

    @Command(name = "provision-local", description = "Provision a local environment")
    public static class EnvironmentProvisionLocal extends AirshipCommand
    {
        @Option(name = "--name", description = "Environment name")
        public String environment;

        @Option(name = "--repository", description = "Repository for binaries and configurations")
        public final List<String> repository = newArrayList();

        @Option(name = "--maven-default-group-id", description = "Default maven group-id")
        public final List<String> mavenDefaultGroupId = newArrayList();

        @Option(name = "--coordinator-id", description = "Coordinator identifier")
        public String coordinatorId;

        @Option(name = "--agent-id", description = "Agent identifier")
        public String agentId;

        @Option(name = "--location", description = "Environment location")
        public String location;

        @Option(name = "--instance-type", description = "Instance type for the local environment")
        public String instanceType;

        @Option(name = "--internal-ip", description = "Internal IP address type for the local environment")
        public String internalIp;

        @Option(name = "--external-address", description = "External address type for the local environment")
        public String externalAddress;

        @Arguments(usage = "<ref> <path>",
                description = "Reference name and path for the environment")
        public List<String> args = newArrayList();

        public void execute()
                throws Exception
        {
            if (args.size() != 2) {
                throw new ParseException("You must specify a name and path.");
            }

            String ref = args.get(0);
            String path = args.get(1);
            if (environment == null) {
                environment = ref;
            }

            String nameProperty = "environment." + ref + ".name";
            String coordinatorProperty = "environment." + ref + ".coordinator";
            String repositoryProperty = "environment." + ref + ".repository";
            String mavenGroupIdProperty = "environment." + ref + ".maven-group-id";

            if (config.get(nameProperty) != null) {
                throw new RuntimeException("Environment " + ref + " already exists");
            }

            new File(path).getAbsoluteFile().mkdirs();

            config.set(nameProperty, environment);
            config.set(coordinatorProperty, path);
            for (String repo : repository) {
                config.add(repositoryProperty, repo);
            }
            for (String groupId : mavenDefaultGroupId) {
                config.add(mavenGroupIdProperty, groupId);
            }
            if (coordinatorId != null) {
                config.set("environment." + ref + ".coordinator-id", coordinatorId);
            }
            if (agentId != null) {
                config.set("environment." + ref + ".agent-id", agentId);
            }
            if (location != null) {
                config.set("environment." + ref + ".location", location);
            }
            if (instanceType != null) {
                config.set("environment." + ref + ".instance-type", instanceType);
            }
            if (internalIp != null) {
                config.set("environment." + ref + ".internal-ip", internalIp);
            }
            if (externalAddress != null) {
                config.set("environment." + ref + ".external-address", externalAddress);
            }
            // make this environment the default environment
            config.set("environment.default", ref);
            config.save();
        }
    }

    @Command(name = "provision-aws", description = "Provision an AWS environment")
    public static class EnvironmentProvisionAws extends AirshipCommand
    {
        @Option(name = "--name", description = "Environment name")
        public String environment;

        @Option(name = "--aws-endpoint", description = "Amazon endpoint URL")
        public String awsEndpoint;

        @Option(name = "--ami", description = "Amazon Machine Image for EC2 instances")
        public String ami = "ami-27b7744e";

        @Option(name = "--key-pair", description = "Key pair for all EC2 instances")
        public String keyPair = "keypair";

        @Option(name = "--security-group", description = "Security group for all EC2 instances")
        public String securityGroup = "default";

        @Option(name = "--availability-zone", description = "EC2 availability zone for coordinator")
        public String availabilityZone;

        @Option(name = "--instance-type", description = "EC2 instance type for coordinator")
        public String instanceType = "t1.micro";

        @Option(name = "--coordinator-config", description = "Configuration for the coordinator")
        public String coordinatorConfig;

        @Option(name = "--repository", description = "Repository for binaries and configurations")
        public final List<String> repository = newArrayList();

        @Option(name = "--maven-default-group-id", description = "Default maven group-id")
        public final List<String> mavenDefaultGroupId = newArrayList();

        @Arguments(description = "Reference name for the environment", required = true)
        public String ref;

        public void execute()
                throws Exception
        {
            Preconditions.checkNotNull(ref, "You must specify a name");

            if (environment == null) {
                environment = ref;
            }

            String nameProperty = "environment." + ref + ".name";

            if (config.get(nameProperty) != null) {
                throw new RuntimeException("Environment " + ref + " already exists");
            }

            Preconditions.checkNotNull(coordinatorConfig, "You must specify the coordinator config");

            String accessKey = config.get("aws.access-key");
            Preconditions.checkNotNull(accessKey, "You must set the aws access-key with: airship config set aws.access-key <key>");
            String secretKey = config.get("aws.secret-key");
            Preconditions.checkNotNull(secretKey, "You must set the aws secret-key with: airship config set aws.secret-key <key>");

            // create the repository
            List<URI> repoBases = ImmutableList.copyOf(Lists.transform(repository, new ToUriFunction()));
            Repository repository = new RepositorySet(ImmutableSet.<Repository>of(
                    new MavenRepository(mavenDefaultGroupId, repoBases),
                    new HttpRepository(repoBases, null, null, null)));

            // use the coordinator configuration to build the provisioner
            // This causes the defaults to be initialized using the new coordinator's configuration
            ConfigurationFactory configurationFactory = createConfigurationFactory(repository, coordinatorConfig);

            // todo print better error message here
            AwsProvisionerConfig awsProvisionerConfig = configurationFactory.build(AwsProvisionerConfig.class);
            CoordinatorConfig coordinatorConfig = configurationFactory.build(CoordinatorConfig.class);
            HttpServerConfig httpServerConfig = configurationFactory.build(HttpServerConfig.class);

            if (awsEndpoint == null) {
                awsEndpoint = awsProvisionerConfig.getAwsEndpoint();
            }

            // generate new keys for the cluster
            AmazonIdentityManagementClient iamClient = new AmazonIdentityManagementClient(new BasicAWSCredentials(accessKey, secretKey));
            if (awsEndpoint != null) {
                iamClient.setEndpoint(awsEndpoint);
            }
            String username = createIamUserForEnvironment(iamClient, environment);

            // save the environment since we just created a permanent resource
            config.set(nameProperty, environment);
            config.set("environment." + ref + ".iam-user", username);
            AWSCredentials environmentCredentials = createIamAccessKey(iamClient, username);

            // Create the provisioner
            AmazonEC2Client ec2Client = new AmazonEC2Client(environmentCredentials);
            if (awsEndpoint != null) {
                ec2Client.setEndpoint(awsEndpoint);
            }
            NodeInfo nodeInfo = new NodeInfo(environment);
            AwsProvisioner provisioner = new AwsProvisioner(environmentCredentials,
                    ec2Client,
                    nodeInfo,
                    new HttpServerInfo(new HttpServerConfig(), nodeInfo),
                    repository,
                    coordinatorConfig,
                    awsProvisionerConfig);

            // provision the coordinator
            List<Instance> instances = provisioner.provisionCoordinator(this.coordinatorConfig,
                    1,
                    instanceType,
                    availabilityZone,
                    ami,
                    keyPair,
                    securityGroup,
                    httpServerConfig.getHttpPort(),
                    awsProvisionerConfig.getAwsCredentialsFile(),
                    this.repository);

            // add the new environment to the command line configuration
            config.set(nameProperty, environment);

            // wait for the instances to start
            instances = waitForInstancesToStart(ec2Client, instances, httpServerConfig.getHttpPort());

            // add the coordinators to the config
            String coordinatorProperty = "environment." + ref + ".coordinator";
            for (Instance instance : instances) {
                config.add(coordinatorProperty, instance.getExternalUri().toASCIIString());
            }

            // make this environment the default environment
            config.set("environment.default", ref);
            config.save();
        }

        private static List<Instance> waitForInstancesToStart(AmazonEC2Client ec2Client, List<Instance> instances, int port)
        {
            List<String> instanceIds = newArrayList();
            for (Instance instance : instances) {
                instanceIds.add(instance.getInstanceId());
            }

            for (int loop = 0; true; loop++) {
                try {
                    DescribeInstancesResult result = ec2Client.describeInstances(new DescribeInstancesRequest().withInstanceIds(instanceIds));
                    if (allInstancesStarted(result, port)) {
                        List<Instance> resolvedInstances = newArrayList();
                        for (Reservation reservation : result.getReservations()) {
                            for (com.amazonaws.services.ec2.model.Instance instance : reservation.getInstances()) {
                                URI internalUri = null;
                                if (instance.getPrivateIpAddress() != null) {
                                    internalUri = uriBuilder().scheme("http").host(instance.getPrivateIpAddress()).port(port).build();
                                }
                                URI externalUri = null;
                                if (instance.getPublicDnsName() != null) {
                                    externalUri = uriBuilder().scheme("http").host(instance.getPublicDnsName()).port(port).build();
                                }
                                resolvedInstances.add(toInstance(instance, internalUri, externalUri, "coordinator"));
                            }
                        }
                        WaitUtils.clearWaitMessage();
                        return resolvedInstances;
                    }
                }
                catch (AmazonClientException ignored) {
                }

                WaitUtils.wait(loop);
            }
        }

        private static AWSCredentials createIamAccessKey(AmazonIdentityManagementClient iamClient, String username)
        {
            CreateAccessKeyResult accessKeyResult = iamClient.createAccessKey(new CreateAccessKeyRequest().withUserName(username));
            AccessKey accessKey = accessKeyResult.getAccessKey();
            return new BasicAWSCredentials(accessKey.getAccessKeyId(), accessKey.getSecretAccessKey());
        }

        private static String createIamUserForEnvironment(AmazonIdentityManagementClient iamClient, String environment)
        {
            String username = format("airship-%s-%s", environment, randomUUID().toString().replace("-", ""));
            String simpleDbName = format("airship-%s", environment);

            iamClient.createUser(new CreateUserRequest(username));

            Map<String, ImmutableList<Object>> policy =
                    ImmutableMap.of("Statement", ImmutableList.builder()
                            .add(ImmutableMap.builder()
                                    .put("Action", ImmutableList.of(
                                            "ec2:CreateTags",
                                            "ec2:DeleteTags",
                                            "ec2:DescribeAvailabilityZones",
                                            "ec2:DescribeInstances",
                                            "ec2:RunInstances",
                                            "ec2:StartInstances",
                                            "ec2:StopInstances",
                                            "ec2:TerminateInstances"
                                    ))
                                    .put("Effect", "Allow")
                                    .put("Resource", "*")
                                    .build())
                            .add(ImmutableMap.builder()
                                    .put("Action", ImmutableList.of(
                                            "sdb:CreateDomain",
                                            "sdb:PutAttributes",
                                            "sdb:BatchDeleteAttributes",
                                            "sdb:DeleteAttributes",
                                            "sdb:Select"
                                    ))
                                    .put("Effect", "Allow")
                                    .put("Resource", "arn:aws:sdb:*:*:domain/" + simpleDbName)
                                    .build())
                            .build()
                    );

            String policyJson = JsonCodec.jsonCodec(Object.class).toJson(policy);

            iamClient.putUserPolicy(new PutUserPolicyRequest(username, "policy", policyJson));
            return username;
        }

        private static final int STATE_PENDING = 0;

        private static boolean allInstancesStarted(DescribeInstancesResult describeInstancesResult, int port)
        {
            for (Reservation reservation : describeInstancesResult.getReservations()) {
                for (com.amazonaws.services.ec2.model.Instance instance : reservation.getInstances()) {
                    if (instance.getState() == null || instance.getState().getCode() == null) {
                        return false;
                    }

                    // is it running?
                    int state = instance.getState().getCode();
                    if (state == STATE_PENDING || instance.getPublicDnsName() == null) {
                        return false;
                    }

                    // can we talk to it yet?
                    try {
                        Resources.toByteArray(new URL(format("http://%s:%s/v1/slot", instance.getPublicDnsName(), port)));
                    }
                    catch (Exception e) {
                        return false;
                    }
                }
            }
            return true;
        }
    }

    @Command(name = "show", description = "Show environment details")
    public static class EnvironmentShow extends AirshipCommand
    {
        @Arguments(description = "Environment to show")
        public String ref;

        @Override
        public void execute()
                throws Exception
        {
            String defaultRef = config.get("environment.default");

            if (ref == null) {
                boolean hasEnvironment = false;
                for (Entry<String, Collection<String>> entry : config) {
                    String property = entry.getKey();
                    List<String> parts = ImmutableList.copyOf(Splitter.on('.').split(property));
                    if (parts.size() == 3 && "environment".equals(parts.get(0)) && "name".equals(parts.get(2))) {
                        String ref = parts.get(1);
                        if (ref.equals(defaultRef)) {
                            System.out.println("* " + ref);
                        }
                        else {
                            System.out.println("  " + ref);
                        }
                        hasEnvironment = true;
                    }
                }
                if (!hasEnvironment) {
                    System.out.println("There are no Airship environments.");
                }
            }
            else {
                String realEnvironmentName = config.get("environment." + ref + ".name");
                if (realEnvironmentName == null) {
                    throw new RuntimeException(String.format("'%s' does not appear to be a airship environment", ref));
                }
                List<String> coordinators = config.getAll("environment." + ref + ".coordinator");
                boolean isDefaultRef = ref.equals(defaultRef);

                if (realEnvironmentName.equals(ref)) {
                    System.out.printf("* Environment: %s%n", ref);
                } else {
                    System.out.printf("* Environment reference: %s%n", ref);
                    System.out.printf("  Environment name: %s%n", realEnvironmentName);
                }
                System.out.printf("  Default environment: %s%n", isDefaultRef);
                for (String coordinator : coordinators) {
                    System.out.printf("  Coordinator: %s%n", coordinator);
                }
            }
        }
    }

    @Command(name = "add", description = "Add an environment")
    public static class EnvironmentAdd extends AirshipCommand
    {
        @Option(name = "--name", description = "Environment name")
        public String environment;

        @Arguments(usage = "<ref> <coordinator-url>",
                description = "Reference name and a coordinator url for the environment")
        public List<String> args;

        @Override
        public void execute()
                throws Exception
        {
            if (args.size() != 2) {
                throw new ParseException("You must specify an environment and a coordinator URL.");
            }

            String ref = args.get(0);
            String coordinatorUrl = args.get(1);

            if (environment == null) {
                environment = ref;
            }

            String nameProperty = "environment." + ref + ".name";

            if (config.get(nameProperty) != null) {
                throw new RuntimeException("Environment " + ref + " already exists");
            }

            config.set(nameProperty, environment);
            config.set("environment." + ref + ".coordinator", coordinatorUrl);
            // make this environment the default environment
            config.set("environment.default", ref);
            config.save();
        }
    }

    @Command(name = "remove", description = "Remove an environment")
    public static class EnvironmentRemove extends AirshipCommand
    {
        @Arguments(description = "Environment to remove")
        public String ref;

        @Override
        public void execute()
                throws Exception
        {
            Preconditions.checkNotNull(ref, "You must specify and environment");

            String keyPrefix = "environment." + ref + ".";
            for (Entry<String, Collection<String>> entry : config) {
                String property = entry.getKey();
                if (property.startsWith(keyPrefix)) {
                    config.unset(property);
                }
            }
            if (ref.equals(config.get("environment.default"))) {
                config.unset("environment.default");
            }
            config.save();
        }
    }

    @Command(name = "use", description = "Set the default environment")
    public static class EnvironmentUse extends AirshipCommand
    {
        @Arguments(description = "Environment to make the default")
        public String ref;

        @Override
        public void execute()
                throws Exception
        {
            Preconditions.checkNotNull(ref, "You must specify an environment");

            String nameProperty = "environment." + ref + ".name";

            if (config.get(nameProperty) == null) {
                throw new IllegalArgumentException("Unknown environment " + ref);
            }
            // make this environment the default environment
            config.set("environment.default", ref);
            config.save();
        }
    }

    @Command(name = "get", description = "Get a configuration value")
    public static class ConfigGet extends AirshipCommand
    {
        @Arguments(description = "Key to get")
        public String key;

        @Override
        public void execute()
                throws Exception
        {
            Preconditions.checkNotNull(key, "You must specify a key.");

            List<String> values = config.getAll(key);
            Preconditions.checkArgument(values.size() < 2, "More than one value for the key %s", key);
            if (!values.isEmpty()) {
                System.out.println(values.get(0));
            }
        }
    }

    @Command(name = "get-all", description = "Get all values of configuration")
    public static class ConfigGetAll extends AirshipCommand
    {
        @Arguments(description = "Key to get")
        public String key;

        @Override
        public void execute()
                throws Exception
        {
            Preconditions.checkNotNull(key, "You must specify a key.");

            List<String> values = config.getAll(key);
            for (String value : values) {
                System.out.println(value);
            }
        }
    }

    @Command(name = "set", description = "Set a configuration value")
    public static class ConfigSet extends AirshipCommand
    {
        @Arguments(usage = "<key> <value>",
                description = "Key-value pair to set")
        public List<String> args;

        @Override
        public void execute()
                throws Exception
        {
            if (args.size() != 2) {
                throw new ParseException("You must specify a key and a value.");
            }

            String key = args.get(0);
            String value = args.get(1);

            config.set(key, value);
            config.save();
        }
    }

    @Command(name = "add", description = "Add a configuration value")
    public static class ConfigAdd extends AirshipCommand
    {
        @Arguments(usage = "<key> <value>",
                description = "Key-value pair to add")
        public List<String> args;

        @Override
        public void execute()
                throws Exception
        {
            if (args.size() != 2) {
                throw new ParseException("You must specify a key and a value.");
            }

            String key = args.get(0);
            String value = args.get(1);

            config.add(key, value);
            config.save();
        }
    }

    @Command(name = "unset", description = "Unset a configuration value")
    public static class ConfigUnset extends AirshipCommand
    {
        @Arguments(description = "Key to unset")
        public String key;

        @Override
        public void execute()
                throws Exception
        {
            Preconditions.checkNotNull(key, "You must specify a key.");

            config.unset(key);
            config.save();
        }
    }

    public static void initializeLogging(boolean debug)
            throws IOException
    {
        // unhook out and err while initializing logging or logger will print to them
        PrintStream out = System.out;
        PrintStream err = System.err;
        try {
            if (debug) {
                Logging logging = new Logging();
                logging.initialize(new LoggingConfiguration());
                // TODO: add public level interface to logging framework
                new LoggingMBean().setLevel("io.airlift.airship", "DEBUG");
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
}
