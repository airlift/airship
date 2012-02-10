package com.proofpoint.galaxy.cli;

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
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.io.NullOutputStream;
import com.google.common.io.Resources;
import com.proofpoint.configuration.ConfigurationFactory;
import com.proofpoint.galaxy.coordinator.AwsProvisioner;
import com.proofpoint.galaxy.coordinator.AwsProvisionerConfig;
import com.proofpoint.galaxy.coordinator.CoordinatorConfig;
import com.proofpoint.galaxy.coordinator.HttpRepository;
import com.proofpoint.galaxy.coordinator.Instance;
import com.proofpoint.galaxy.coordinator.MavenRepository;
import com.proofpoint.galaxy.shared.Assignment;
import com.proofpoint.galaxy.shared.Repository;
import com.proofpoint.galaxy.shared.RepositorySet;
import com.proofpoint.galaxy.shared.UpgradeVersions;
import com.proofpoint.galaxy.cli.CommanderFactory.ToUriFunction;
import com.proofpoint.http.server.HttpServerConfig;
import com.proofpoint.json.JsonCodec;
import com.proofpoint.log.Logging;
import com.proofpoint.log.LoggingConfiguration;
import com.proofpoint.node.NodeInfo;
import org.iq80.cli.Arguments;
import org.iq80.cli.Cli;
import org.iq80.cli.Cli.CliBuilder;
import org.iq80.cli.Command;
import org.iq80.cli.Help;
import org.iq80.cli.Option;
import org.iq80.cli.ParseException;

import javax.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.net.URI;
import java.net.URL;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

import static com.google.common.base.Objects.firstNonNull;
import static com.google.common.collect.Lists.newArrayList;
import static com.proofpoint.galaxy.coordinator.AwsProvisioner.toInstance;
import static com.proofpoint.galaxy.shared.ConfigUtils.createConfigurationFactory;
import static com.proofpoint.galaxy.shared.SlotLifecycleState.RESTARTING;
import static com.proofpoint.galaxy.shared.SlotLifecycleState.RUNNING;
import static com.proofpoint.galaxy.shared.SlotLifecycleState.STOPPED;
import static com.proofpoint.galaxy.cli.Column.binary;
import static com.proofpoint.galaxy.cli.Column.config;
import static com.proofpoint.galaxy.cli.Column.ip;
import static com.proofpoint.galaxy.cli.Column.location;
import static com.proofpoint.galaxy.cli.Column.shortId;
import static com.proofpoint.galaxy.cli.Column.status;
import static com.proofpoint.galaxy.cli.Column.statusMessage;
import static java.lang.String.format;
import static java.util.UUID.randomUUID;
import static org.iq80.cli.Cli.buildCli;

public class Galaxy
{
    private static final File CONFIG_FILE = new File(System.getProperty("user.home", "."), ".galaxyconfig");

    public static void main(String[] args)
            throws Exception
    {
        CliBuilder<GalaxyCommand> builder = buildCli("galaxy", GalaxyCommand.class)
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
                        CoordinatorProvisionCommand.class);

        builder.withGroup("agent")
                .withDescription("Manage agents")
                .withDefaultCommand(AgentShowCommand.class)
                .withCommands(AgentShowCommand.class,
                        AgentProvisionCommand.class,
                        AgentTerminateCommand.class);

        builder.withGroup("environment")
                .withDescription("Manage environments")
                .withDefaultCommand(HelpCommand.class)
                .withCommands(EnvironmentProvisionLocal.class,
                        EnvironmentProvisionAws.class,
                        EnvironmentUse.class,
                        EnvironmentAdd.class);

        builder.withGroup("config")
                .withDescription("Manage configuration")
                .withDefaultCommand(HelpCommand.class)
                .withCommands(ConfigGet.class,
                        ConfigGetAll.class,
                        ConfigSet.class,
                        ConfigAdd.class,
                        ConfigUnset.class);

        Cli<GalaxyCommand> galaxyParser = builder.build();

        galaxyParser.parse(args).call();
    }

    public static abstract class GalaxyCommand implements Callable<Void>
    {
        @Inject
        public GlobalOptions globalOptions = new GlobalOptions();

        @Override
        public final Void call()
                throws Exception
        {
            initializeLogging(globalOptions.debug);

            try {
                execute();
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

        protected abstract void execute() throws Exception;
    }

    public static abstract class GalaxyCommanderCommand extends GalaxyCommand
    {
        @Override
        public void execute()
                throws Exception
        {
            initializeLogging(globalOptions.debug);

            Config config = Config.loadConfig(CONFIG_FILE);

            String ref = globalOptions.environment;
            if (ref == null) {
                ref = config.get("environment.default");
            }
            if (ref == null) {
                throw new RuntimeException("You must specify an environment.");
            }
            String environment = config.get("environment." + ref + ".name");
            if (environment == null) {
                throw new RuntimeException("Unknown environment " + ref);
            }
            String coordinator = config.get("environment." + ref + ".coordinator");
            if (coordinator == null) {
                throw new RuntimeException("Environment " + ref + " does not have a coordinator url.  You can add a coordinator url with galaxy coordinator add <url>");
            }

            URI coordinatorUri = new URI(coordinator);

            CommanderFactory commanderFactory = new CommanderFactory()
                    .setEnvironment(environment)
                    .setCoordinatorUri(coordinatorUri)
                    .setRepositories(config.getAll("environment." + ref + ".repository"))
                    .setMavenDefaultGroupIds(config.getAll("environment." + ref + ".maven-group-id"));

            if (config.get("environment." + ref + ".agent-id") != null) {
                commanderFactory.setAgentId(config.get("environment." + ref + ".agent-id"));
            }
            if (config.get("environment." + ref + ".location") != null) {
                commanderFactory.setLocation(config.get("environment." + ref + ".location"));
            }
            if (config.get("environment." + ref + ".instance-type") != null) {
                commanderFactory.setInstanceType(config.get("environment." + ref + ".instance-type"));
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
                TablePrinter tablePrinter = new TablePrinter(shortId, ip, status, Column.instanceType, location);
                tablePrinter.print(agents);
            }
        }

        public void displayCoordinators(Iterable<Record> coordinators)
        {
            if (Iterables.isEmpty(coordinators)) {
                System.out.println("No coordinators match the provided filters.");
            }
            else {
                TablePrinter tablePrinter = new TablePrinter(shortId, ip, status, Column.instanceType, location);
                tablePrinter.print(coordinators);
            }
        }
    }

    @Command(name = "help", description = "Display help information about galaxy")
    public static class HelpCommand extends GalaxyCommand
    {
        @Inject
        public Help help;

        @Override
        protected void execute()
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
    public static class ShowCommand extends GalaxyCommanderCommand
    {
        @Inject
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
    public static class InstallCommand extends GalaxyCommanderCommand
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
    public static class UpgradeCommand extends GalaxyCommanderCommand
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
    public static class TerminateCommand extends GalaxyCommanderCommand
    {
        @Inject
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
    public static class StartCommand extends GalaxyCommanderCommand
    {
        @Inject
        public final SlotFilter slotFilter = new SlotFilter();

        @Override
        public void execute(Commander commander)
        {
            List<Record> slots = commander.setState(slotFilter, RUNNING);
            displaySlots(slots);
        }
    }

    @Command(name = "stop", description = "Stop a server")
    public static class StopCommand extends GalaxyCommanderCommand
    {
        @Inject
        public final SlotFilter slotFilter = new SlotFilter();

        @Override
        public void execute(Commander commander)
        {
            List<Record> slots = commander.setState(slotFilter, STOPPED);
            displaySlots(slots);
        }
    }

    @Command(name = "restart", description = "Restart server")
    public static class RestartCommand extends GalaxyCommanderCommand
    {
        @Inject
        public final SlotFilter slotFilter = new SlotFilter();

        @Override
        public void execute(Commander commander)
        {
            List<Record> slots = commander.setState(slotFilter, RESTARTING);
            displaySlots(slots);
        }
    }

    @Command(name = "reset-to-actual", description = "Reset slot expected state to actual")
    public static class ResetToActualCommand extends GalaxyCommanderCommand
    {
        @Inject
        public final SlotFilter slotFilter = new SlotFilter();

        @Override
        public void execute(Commander commander)
        {
            List<Record> slots = commander.resetExpectedState(slotFilter);
            displaySlots(slots);
        }
    }

    @Command(name = "ssh", description = "ssh to slot installation")
    public static class SshCommand extends GalaxyCommanderCommand
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
    public static class CoordinatorShowCommand extends GalaxyCommanderCommand
    {
        @Inject
        public final CoordinatorFilter coordinatorFilter = new CoordinatorFilter();

        @Override
        public void execute(Commander commander)
                throws Exception
        {
            List<Record> coordinators = commander.showCoordinators(coordinatorFilter);
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
    public static class CoordinatorProvisionCommand extends GalaxyCommanderCommand
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

        @Override
        public void execute(Commander commander)
                throws Exception
        {
            List<Record> coordinators = commander.provisionCoordinators(coordinatorConfig, count, instanceType, availabilityZone, ami, keyPair, securityGroup);
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

    @Command(name = "show", description = "Show agent details")
    public static class AgentShowCommand extends GalaxyCommanderCommand
    {
        @Inject
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

    @Command(name = "provision", description = "Provision a new agent")
    public static class AgentProvisionCommand extends GalaxyCommanderCommand
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
            List<Record> agents = commander.provisionAgents(count, availabilityZone, instanceType);
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

    @Command(name = "terminate", description = "Terminate an agent")
    public static class AgentTerminateCommand extends GalaxyCommanderCommand
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

    @Command(name = "provision-local", description = "Provision a local environment")
    public static class EnvironmentProvisionLocal extends GalaxyCommand
    {
        @Option(name = "--name", description = "Environment name")
        public String environment;

        @Option(name = "--repository", description = "Repository for binaries and configurations")
        public final List<String> repository = newArrayList();

        @Option(name = "--maven-default-group-id", description = "Default maven group-id")
        public final List<String> mavenDefaultGroupId = newArrayList();

        @Option(name = "--agent-id", description = "Agent identifier")
        public String agentId;

        @Option(name = "--location", description = "Environment location")
        public String location;

        @Option(name = "--instance-type", description = "Instance type for the local environment")
        public String instanceType;

        @Arguments(usage = "<ref> <path>",
                description = "Reference name and path for the environment")
        public List<String> args;

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

            Config config = Config.loadConfig(CONFIG_FILE);
            if (config.get(nameProperty) != null) {
                throw new RuntimeException("Environment " + ref + " already exists");
            }
            config.set(nameProperty, environment);
            config.set(coordinatorProperty, path);
            for (String repo : repository) {
                config.add(repositoryProperty, repo);
            }
            for (String groupId : mavenDefaultGroupId) {
                config.add(mavenGroupIdProperty, groupId);
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
            if (config.get("environment.default") == null) {
                config.set("environment.default", ref);
            }
            config.save(CONFIG_FILE);
        }
    }

    @Command(name = "provision-aws", description = "Provision an AWS environment")
    public static class EnvironmentProvisionAws extends GalaxyCommand
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

            Config config = Config.loadConfig(CONFIG_FILE);
            if (config.get(nameProperty) != null) {
                throw new RuntimeException("Environment " + ref + " already exists");
            }

            Preconditions.checkNotNull(coordinatorConfig, "You must specify the coordinator config");

            String accessKey = config.get("aws.access-key");
            Preconditions.checkNotNull(accessKey, "You must set the aws access-key with: galaxy config set aws.access-key <key>");
            String secretKey = config.get("aws.secret-key");
            Preconditions.checkNotNull(secretKey, "You must set the aws secret-key with: galaxy config set aws.secret-key <key>");

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
            AwsProvisioner provisioner = new AwsProvisioner(environmentCredentials,
                    ec2Client,
                    new NodeInfo(environment),
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
                config.set(coordinatorProperty, instance.getUri().toASCIIString());
            }

            // make this environment the default if there are no other environments
            if (config.get("environment.default") == null) {
                config.set("environment.default", ref);
            }
            config.save(CONFIG_FILE);
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
                                URI uri = null;
                                if (instance.getPublicDnsName() != null) {
                                    uri = URI.create(format("http://%s:%s", instance.getPublicDnsName(), port));
                                }
                                resolvedInstances.add(toInstance(instance, uri, "coordinator"));
                            }
                        }
                        System.out.print("\r \n");
                        return resolvedInstances;
                    }
                }
                catch (AmazonClientException ignored) {
                }

                switch (loop % 5) {
                    case 0:
                        System.out.print("\r                            \r");
                        break;
                    case 1:
                        System.out.print("\r1.");
                        break;
                    case 2:
                        System.out.print(" 2.");
                        break;
                    case 3:
                        System.out.print(" 3.");
                        break;
                    case 4:
                        System.out.print("  GO!");
                        break;
                }
                try {
                    Thread.sleep(500);
                }
                catch (InterruptedException e) {
                }
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
            String username = format("galaxy-%s-%s", environment, randomUUID().toString().replace("-", ""));
            String simpleDbName = format("galaxy-%s", environment);

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

    @Command(name = "add", description = "Add an environment")
    public static class EnvironmentAdd extends GalaxyCommand
    {
        @Option(name = "--name", description = "Environment name")
        public String environment;

        @Arguments(usage = "<ref> <coordinator-url>",
                description = "Reference name and a coordinator url for the environment")
        public List<String> args;

        @Override
        protected void execute()
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

            Config config = Config.loadConfig(CONFIG_FILE);
            if (config.get(nameProperty) != null) {
                throw new RuntimeException("Environment " + ref + " already exists");
            }

            config.set(nameProperty, environment);
            config.set("environment." + ref + ".coordinator", coordinatorUrl);
            config.save(CONFIG_FILE);
        }
    }

    @Command(name = "use", description = "Set the default environment")
    public static class EnvironmentUse extends GalaxyCommand
    {
        @Arguments(description = "Environment to make the default")
        public String ref;

        @Override
        protected void execute()
                throws Exception
        {
            Preconditions.checkNotNull(ref, "You must specify an environment");

            String nameProperty = "environment." + ref + ".name";

            Config config = Config.loadConfig(CONFIG_FILE);
            if (config.get(nameProperty) == null) {
                throw new IllegalArgumentException("Unknown environment " + ref);
            }
            config.set("environment.default", ref);
            config.save(CONFIG_FILE);
        }
    }

    @Command(name = "get", description = "Get a configuration value")
    public static class ConfigGet extends GalaxyCommand
    {
        @Arguments(description = "Key to get")
        public String key;

        @Override
        protected void execute()
                throws Exception
        {
            Preconditions.checkNotNull(key, "You must specify a key.");

            Config config = Config.loadConfig(CONFIG_FILE);
            List<String> values = config.getAll(key);
            Preconditions.checkArgument(values.size() < 2, "More than one value for the key %s", key);
            if (!values.isEmpty()) {
                System.out.println(values.get(0));
            }
        }
    }

    @Command(name = "get-all", description = "Get all values of configuration")
    public static class ConfigGetAll extends GalaxyCommand
    {
        @Arguments(description = "Key to get")
        public String key;

        @Override
        protected void execute()
                throws Exception
        {
            Preconditions.checkNotNull(key, "You must specify a key.");

            Config config = Config.loadConfig(CONFIG_FILE);
            List<String> values = config.getAll(key);
            for (String value : values) {
                System.out.println(value);
            }
        }
    }

    @Command(name = "set", description = "Set a configuration value")
    public static class ConfigSet extends GalaxyCommand
    {
        @Arguments(usage = "<key> <value>",
                description = "Key-value pair to set")
        public List<String> args;

        @Override
        protected void execute()
                throws Exception
        {
            if (args.size() != 2) {
                throw new ParseException("You must specify a key and a value.");
            }

            String key = args.get(0);
            String value = args.get(1);

            Config config = Config.loadConfig(CONFIG_FILE);
            config.set(key, value);
            config.save(CONFIG_FILE);
        }
    }

    @Command(name = "add", description = "Add a configuration value")
    public static class ConfigAdd extends GalaxyCommand
    {
        @Arguments(usage = "<key> <value>",
                description = "Key-value pair to add")
        public List<String> args;

        @Override
        protected void execute()
                throws Exception
        {
            if (args.size() != 2) {
                throw new ParseException("You must specify a key and a value.");
            }

            String key = args.get(0);
            String value = args.get(1);

            Config config = Config.loadConfig(CONFIG_FILE);
            config.add(key, value);
            config.save(CONFIG_FILE);
        }
    }

    @Command(name = "unset", description = "Unset a configuration value")
    public static class ConfigUnset extends GalaxyCommand
    {
        @Arguments(description = "Key to unset")
        public String key;

        @Override
        protected void execute()
                throws Exception
        {
            Preconditions.checkNotNull(key, "You must specify a key.");

            Config config = Config.loadConfig(CONFIG_FILE);
            config.unset(key);
            config.save(CONFIG_FILE);
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
