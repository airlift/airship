package com.proofpoint.galaxy.coordinator;

import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.model.BlockDeviceMapping;
import com.amazonaws.services.ec2.model.CreateTagsRequest;
import com.amazonaws.services.ec2.model.DescribeInstancesResult;
import com.amazonaws.services.ec2.model.Placement;
import com.amazonaws.services.ec2.model.Reservation;
import com.amazonaws.services.ec2.model.RunInstancesRequest;
import com.amazonaws.services.ec2.model.RunInstancesResult;
import com.amazonaws.services.ec2.model.Tag;
import com.amazonaws.services.ec2.model.TerminateInstancesRequest;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Charsets;
import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.CharStreams;
import com.google.common.io.InputSupplier;
import com.proofpoint.galaxy.shared.MavenCoordinates;
import com.proofpoint.galaxy.shared.Repository;
import com.proofpoint.log.Logger;
import com.proofpoint.node.NodeInfo;
import org.apache.commons.codec.binary.Base64;

import javax.inject.Inject;
import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.collect.Lists.newArrayList;
import static com.proofpoint.galaxy.shared.ConfigUtils.newConfigEntrySupplier;
import static com.proofpoint.galaxy.shared.MavenCoordinates.DEFAULT_CONFIG_PACKAGING;
import static java.lang.String.format;
import static java.util.Collections.addAll;

public class AwsProvisioner implements Provisioner
{
    private static final Logger log = Logger.get(AwsProvisioner.class);

    private final AmazonEC2 ec2Client;
    private final String environment;
    private final String galaxyVersion;
    private List<String> repositories;

    private String awsEndpoint;

    private String accessKey;
    private String secretKey;

    private final String coordinatorAmi;
    private final String coordinatorKeypair;
    private final String coordinatorSecurityGroup;
    private final String coordinatorDefaultInstanceType;
    private final int coordinatorDefaultPort;

    private final String agentAmi;
    private final String agentKeypair;
    private final String agentSecurityGroup;
    private final String agentDefaultInstanceType;
    private final int agentDefaultPort;

    private final Repository repository;

    private final Set<String> invalidInstances = Collections.newSetFromMap(new ConcurrentHashMap<String, Boolean>());

    @Inject
    public AwsProvisioner(AmazonEC2 ec2Client,
            NodeInfo nodeInfo,
            Repository repository,
            CoordinatorConfig coordinatorConfig,
            AwsProvisionerConfig awsProvisionerConfig)
    {
        this.ec2Client = checkNotNull(ec2Client, "ec2Client is null");

        checkNotNull(nodeInfo, "nodeInfo is null");
        this.environment = nodeInfo.getEnvironment();

        checkNotNull(coordinatorConfig, "coordinatorConfig is null");
        galaxyVersion = coordinatorConfig.getGalaxyVersion();
        repositories = coordinatorConfig.getRepositories();

        checkNotNull(awsProvisionerConfig, "awsConfig is null");

        awsEndpoint = awsProvisionerConfig.getAwsEndpoint();

        accessKey = awsProvisionerConfig.getAwsAccessKey();
        secretKey = awsProvisionerConfig.getAwsSecretKey();

        coordinatorAmi = awsProvisionerConfig.getAwsCoordinatorAmi();
        coordinatorKeypair = awsProvisionerConfig.getAwsCoordinatorKeypair();
        coordinatorSecurityGroup = awsProvisionerConfig.getAwsCoordinatorSecurityGroup();
        coordinatorDefaultInstanceType = awsProvisionerConfig.getAwsCoordinatorDefaultInstanceType();
        coordinatorDefaultPort = awsProvisionerConfig.getAwsCoordinatorDefaultPort();

        agentAmi = awsProvisionerConfig.getAwsAgentAmi();
        agentKeypair = awsProvisionerConfig.getAwsAgentKeypair();
        agentSecurityGroup = awsProvisionerConfig.getAwsAgentSecurityGroup();
        agentDefaultInstanceType = awsProvisionerConfig.getAwsAgentDefaultInstanceType();
        agentDefaultPort = awsProvisionerConfig.getAwsAgentDefaultPort();

        this.repository = checkNotNull(repository, "repository is null");
    }

    public AwsProvisioner(AmazonEC2 ec2Client,
            String environment,
            String galaxyVersion,
            List<String> repositories,
            String awsEndpoint,
            String accessKey,
            String secretKey,
            String coordinatorAmi,
            String coordinatorKeypair,
            String coordinatorSecurityGroup,
            String coordinatorDefaultInstanceType,
            int coordinatorDefaultPort,
            String agentAmi,
            String agentKeypair,
            String agentSecurityGroup, String agentDefaultInstanceType, int agentDefaultPort, Repository repository)
    {
        Preconditions.checkNotNull(ec2Client, "ec2Client is null");
        Preconditions.checkNotNull(environment, "environment is null");
        Preconditions.checkNotNull(galaxyVersion, "galaxyVersion is null");

        this.ec2Client = ec2Client;
        this.environment = environment;
        this.galaxyVersion = galaxyVersion;
        this.repositories = repositories;

        this.awsEndpoint = awsEndpoint;

        this.accessKey = accessKey;
        this.secretKey = secretKey;

        this.coordinatorAmi = coordinatorAmi;
        this.coordinatorKeypair = coordinatorKeypair;
        this.coordinatorSecurityGroup = coordinatorSecurityGroup;
        this.coordinatorDefaultInstanceType = coordinatorDefaultInstanceType;
        this.coordinatorDefaultPort = coordinatorDefaultPort;

        this.agentAmi = agentAmi;
        this.agentKeypair = agentKeypair;
        this.agentSecurityGroup = agentSecurityGroup;
        this.agentDefaultInstanceType = agentDefaultInstanceType;
        this.agentDefaultPort = agentDefaultPort;

        this.repository = repository;
    }

    @Override
    public List<Instance> listAgents()
    {
        DescribeInstancesResult describeInstancesResult = ec2Client.describeInstances();
        List<Reservation> reservations = describeInstancesResult.getReservations();
        List<Instance> instances = newArrayList();

        for (Reservation reservation : reservations) {
            for (com.amazonaws.services.ec2.model.Instance instance : reservation.getInstances()) {
                // skip terminated instances
                if ("terminated".equalsIgnoreCase(instance.getState().getName())) {
                    continue;
                }
                Map<String, String> tags = toMap(instance.getTags());
                if ("agent".equals(tags.get("galaxy:role")) && environment.equals(tags.get("galaxy:environment"))) {
                    String portTag = tags.get("galaxy:port");
                    if (portTag == null) {
                        if (invalidInstances.add(instance.getInstanceId())) {
                            log.error("Instance %s does not have a galaxy:port tag", instance.getInstanceId());
                        }
                        continue;
                    }

                    int port;
                    try {
                        port = Integer.parseInt(portTag);
                    }
                    catch (Exception e) {
                        if (invalidInstances.add(instance.getInstanceId())) {
                            log.error("Instance %s galaxy:port tag is not a number", instance.getInstanceId());
                        }
                        continue;
                    }

                    URI uri = null;
                    if (instance.getPrivateIpAddress() != null) {
                        uri = URI.create(format("http://%s:%s", instance.getPrivateIpAddress(), port));
                    }
                    instances.add(toInstance(instance, uri));
                    invalidInstances.remove(instance.getInstanceId());
                }
            }
        }
        return instances;
    }

    public List<Instance> provisionCoordinator(String instanceType, String availabilityZone)
            throws Exception
    {
        if (instanceType == null) {
            instanceType = coordinatorDefaultInstanceType;
        }

        List<BlockDeviceMapping> blockDeviceMappings = ImmutableList.<BlockDeviceMapping>builder()
                .add(new BlockDeviceMapping().withVirtualName("ephemeral0").withDeviceName("/dev/sdb"))
                .add(new BlockDeviceMapping().withVirtualName("ephemeral1").withDeviceName("/dev/sdc"))
                .add(new BlockDeviceMapping().withVirtualName("ephemeral2").withDeviceName("/dev/sdd"))
                .add(new BlockDeviceMapping().withVirtualName("ephemeral3").withDeviceName("/dev/sde"))
                .build();

        RunInstancesRequest request = new RunInstancesRequest()
                .withImageId(coordinatorAmi)
                .withKeyName(coordinatorKeypair)
                .withSecurityGroups(coordinatorSecurityGroup)
                .withInstanceType(instanceType)
                .withUserData(getCoordinatorUserData(instanceType))
                .withBlockDeviceMappings(blockDeviceMappings)
                .withMinCount(1)
                .withMaxCount(1);

        if (availabilityZone != null) {
            request.withPlacement(new Placement(availabilityZone));
        }

        log.debug("launching instances: %s", request);
        RunInstancesResult result = ec2Client.runInstances(request);
        log.debug("launched instances: %s", result);

        List<Instance> instances = newArrayList();
        List<String> instanceIds = newArrayList();

        for (com.amazonaws.services.ec2.model.Instance instance : result.getReservation().getInstances()) {
            instances.add(toInstance(instance, null));
            instanceIds.add(instance.getInstanceId());
        }

        List<Tag> tags = ImmutableList.<Tag>builder()
                .add(new Tag("Name", format("galaxy-%s-coordinator", environment)))
                .add(new Tag("galaxy:role", "coordinator"))
                .add(new Tag("galaxy:environment", environment))
                .add(new Tag("galaxy:port", String.valueOf(coordinatorDefaultPort)))
                .build();
        createInstanceTagsWithRetry(instanceIds, tags);

        return instances;
    }

    @Override
    public List<Instance> provisionAgents(int agentCount, String instanceType, String availabilityZone)
            throws Exception
    {
        if (instanceType == null) {
            instanceType = agentDefaultInstanceType;
        }

        List<BlockDeviceMapping> blockDeviceMappings = ImmutableList.<BlockDeviceMapping>builder()
                .add(new BlockDeviceMapping().withVirtualName("ephemeral0").withDeviceName("/dev/sdb"))
                .add(new BlockDeviceMapping().withVirtualName("ephemeral1").withDeviceName("/dev/sdc"))
                .add(new BlockDeviceMapping().withVirtualName("ephemeral2").withDeviceName("/dev/sdd"))
                .add(new BlockDeviceMapping().withVirtualName("ephemeral3").withDeviceName("/dev/sde"))
                .build();

        RunInstancesRequest request = new RunInstancesRequest()
                .withImageId(agentAmi)
                .withKeyName(agentKeypair)
                .withSecurityGroups(agentSecurityGroup)
                .withInstanceType(instanceType)
                .withPlacement(new Placement(availabilityZone))
                .withUserData(getAgentUserData(instanceType))
                .withBlockDeviceMappings(blockDeviceMappings)
                .withMinCount(agentCount)
                .withMaxCount(agentCount);

        log.debug("launching instances: %s", request);
        RunInstancesResult result = ec2Client.runInstances(request);
        log.debug("launched instances: %s", result);

        List<Instance> instances = newArrayList();
        List<String> instanceIds = newArrayList();

        for (com.amazonaws.services.ec2.model.Instance instance : result.getReservation().getInstances()) {
            instances.add(toInstance(instance, null));
            instanceIds.add(instance.getInstanceId());
        }

        List<Tag> tags = ImmutableList.<Tag>builder()
                .add(new Tag("Name", format("galaxy-%s-agent", environment)))
                .add(new Tag("galaxy:role", "agent"))
                .add(new Tag("galaxy:environment", environment))
                .add(new Tag("galaxy:port", String.valueOf(agentDefaultPort)))
                .build();
        createInstanceTagsWithRetry(instanceIds, tags);

        return instances;
    }

    @Override
    public void terminateAgents(List<String> instanceIds)
    {
        ec2Client.terminateInstances(new TerminateInstancesRequest(instanceIds));
    }

    private void createInstanceTagsWithRetry(List<String> instanceIds, List<Tag> tags)
    {
        Exception lastException = null;
        for (int i = 0; i < 5; i++) {
            try {
                ec2Client.createTags(new CreateTagsRequest(instanceIds, tags));
                return;
            }
            catch (Exception e) {
                lastException = e;
            }
        }
        log.error(lastException, "failed to create tags for instances: %s", instanceIds);
    }

    private String getAgentUserData(String instanceType)
    {
        return encodeBase64(getRawAgentUserData(instanceType));
    }

    @VisibleForTesting
    String getRawAgentUserData(String instanceType)
    {
        String boundary = "===============884613ba9e744d0c851955611107553e==";
        String boundaryLine = "--" + boundary;
        String mimeVersion = "MIME-Version: 1.0";
        String encoding = "Content-Transfer-Encoding: 7bit";
        String contentTypeUrl = "Content-Type: text/x-include-url; charset=\"us-ascii\"";
        String contentTypeText = "Content-Type: text/plain; charset=\"us-ascii\"";
        String attachmentFormat = "Content-Disposition: attachment; filename=\"%s\"";

        URI partHandler = getRequiredUri(repository, new MavenCoordinates("com.proofpoint.galaxy", "galaxy-ec2", galaxyVersion, "py", "part-handler", null));
        URI installScript = getRequiredUri(repository, new MavenCoordinates("com.proofpoint.galaxy", "galaxy-ec2", galaxyVersion, "rb", "install", null));

        List<String> lines = newArrayList();
        addAll(lines,
                "Content-Type: multipart/mixed; boundary=\"" + boundary + "\"", mimeVersion,
                "",
                boundaryLine,

                contentTypeUrl, mimeVersion, encoding, format(attachmentFormat, "galaxy-part-handler.py"), "",
                partHandler.toString(),
                "",
                boundaryLine,

                contentTypeText, mimeVersion, encoding, format(attachmentFormat, "installer.properties"), "",
                property("galaxy.version", galaxyVersion),
                property("environment", environment),
                "artifacts=galaxy-agent",
                "",
                boundaryLine,

                contentTypeText, mimeVersion, encoding, format(attachmentFormat, "galaxy-agent.properties"), "",
                property("http-server.http.port", agentDefaultPort),
                "",
                boundaryLine,

                contentTypeUrl, mimeVersion, encoding, format(attachmentFormat, "galaxy-install.rb"), "",
                installScript.toString(),
                "",
                boundaryLine
        );


        String configArtifactId = "agent";
        if (instanceType != null) {
            configArtifactId = configArtifactId + "-" + instanceType;
        }
        InputSupplier<? extends InputStream> resourcesFile = newConfigEntrySupplier(repository,
                new MavenCoordinates(null, configArtifactId, galaxyVersion, DEFAULT_CONFIG_PACKAGING, null, null).toGAV(),
                "etc/resources.properties");
        if (resourcesFile != null) {
            try {
                String resourcesProperties = CharStreams.toString(CharStreams.newReaderSupplier(resourcesFile, Charsets.UTF_8));
                addAll(lines,
                        contentTypeUrl, mimeVersion, encoding, format(attachmentFormat, "galaxy-agent-resources.properties"), "",
                        resourcesProperties,
                        "",
                        boundaryLine);
            }
            catch (FileNotFoundException ignored) {
            }
            catch (IOException e) {
                throw new RuntimeException("Error reading agent resources file");
            }
        }
        return Joiner.on('\n').skipNulls().join(lines);
    }

    private String getCoordinatorUserData(String instanceType)
    {
        return encodeBase64(getRawCoordinatorUserData(instanceType));
    }

    @VisibleForTesting
    String getRawCoordinatorUserData(String instanceType)
    {
        // create coordinator config file
        // todo this is only needed of a configuration is not provided for the coordinator
        byte[] coordinatorConfig = createCoordinatorConfig();

        String boundary = "===============884613ba9e744d0c851955611107553e==";
        String boundaryLine = "--" + boundary;
        String mimeVersion = "MIME-Version: 1.0";
        String encodingText = "Content-Transfer-Encoding: 7bit";
        String contentExecUrl = "Content-Type: text/x-include-url; charset=\"us-ascii\"";
        String contentDownloadUrl = "Content-Type: text/x-url";
        String contentTypeText = "Content-Type: text/plain; charset=\"us-ascii\"";
        String attachmentFormat = "Content-Disposition: attachment; filename=\"%s\"";

        String contentTypeBinary = "Content-Type: application/octet-stream";
        String encodingBase64 = "Content-Transfer-Encoding: base64";


        URI partHandler = getRequiredUri(repository, new MavenCoordinates("com.proofpoint.galaxy", "galaxy-ec2", galaxyVersion, "py", "part-handler", null));
        URI galaxyCli = getRequiredUri(repository, new MavenCoordinates("com.proofpoint.galaxy", "galaxy-standalone", galaxyVersion, "jar", "executable", null));
        URI coordinatorInstall = getRequiredUri(repository, new MavenCoordinates("com.proofpoint.galaxy", "galaxy-ec2", galaxyVersion, "sh", "install", null));
        URI coordinatorInstallPrep = getRequiredUri(repository, new MavenCoordinates("com.proofpoint.galaxy", "galaxy-ec2", galaxyVersion, "sh", "install-prep", null));

        List<String> lines = newArrayList();
        addAll(lines,
                "Content-Type: multipart/mixed; boundary=\"" + boundary + "\"", mimeVersion,
                "",
                boundaryLine,

                contentExecUrl, mimeVersion, encodingText, format(attachmentFormat, "galaxy-part-handler.py"), "",
                partHandler.toString(),
                "",
                boundaryLine,

                contentTypeBinary, mimeVersion, encodingBase64, format(attachmentFormat, "galaxy-coordinator.config"), "",
                Base64.encodeBase64String(coordinatorConfig),
                "",
                "",
                boundaryLine,

                contentDownloadUrl, mimeVersion, encodingText, format(attachmentFormat, "galaxy"), "",
                galaxyCli.toASCIIString(),
                "",
                boundaryLine,

                contentTypeText, mimeVersion, encodingText, format(attachmentFormat, "installer.properties"), "",
                property("galaxyEnvironment", environment),
                property("galaxyInstallBinary", "com.proofpoint.galaxy:galaxy-coordinator:" + galaxyVersion),
                property("galaxyInstallConfig", "@galaxy-coordinator.config"),
                "",
                boundaryLine,

                contentDownloadUrl, mimeVersion, encodingText, format(attachmentFormat, "galaxy-install.sh"), "",
                coordinatorInstall.toASCIIString(),
                "",
                boundaryLine ,

                contentExecUrl, mimeVersion, encodingText, format(attachmentFormat, "galaxy-install-prep.sh"), "",
                coordinatorInstallPrep.toASCIIString(),
                "",
                boundaryLine

        );

        return Joiner.on('\n').skipNulls().join(lines);
    }

    private static URI getRequiredUri(Repository repository, MavenCoordinates mavenCoordinates)
    {
        URI uri = repository.binaryToHttpUri(mavenCoordinates.toGAV());
        Preconditions.checkArgument(uri != null, "Could not find %s in repository %s", mavenCoordinates.toGAV(), repository);
        return uri;
    }

    private Instance toInstance(com.amazonaws.services.ec2.model.Instance instance, URI uri)
    {
        return new Instance(instance.getInstanceId(), instance.getInstanceType(), getLocation(instance), uri);
    }

    private static String getLocation(com.amazonaws.services.ec2.model.Instance instance)
    {
        String zone = instance.getPlacement().getAvailabilityZone();
        String region = zone.substring(0, zone.length() - 1);
        return Joiner.on('/').join("ec2", region, zone, instance.getInstanceId(), "agent");
    }

    private static String encodeBase64(String s)
    {
        return Base64.encodeBase64String(s.getBytes(Charsets.UTF_8));
    }

    private Map<String, String> toMap(List<Tag> tags)
    {
        ImmutableMap.Builder<String, String> builder = ImmutableMap.builder();
        for (Tag tag : tags) {
            builder.put(tag.getKey(), tag.getValue());
        }
        return builder.build();
    }

    public byte[] createCoordinatorConfig()
    {
        try {
            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            ZipOutputStream out = new ZipOutputStream(byteArrayOutputStream);

            ZipEntry entry = new ZipEntry("etc/");
            out.putNextEntry(entry);

            entry = new ZipEntry("etc/jvm.config");
            out.putNextEntry(entry);

            entry = new ZipEntry("etc/config.properties");
            out.putNextEntry(entry);


            List<String> lines = newArrayList();
            addAll(lines,
                    property("galaxy.version", galaxyVersion),
                    property("http-server.http.port", coordinatorDefaultPort),
                    "",
                    property("coordinator.repository", Joiner.on(",").skipNulls().join(repositories)),
                    "",
                    "coordinator.provisioner=aws",
                    property("coordinator.aws.endpoint", awsEndpoint),
                    "",
                    property("coordinator.aws.access-key", accessKey),
                    property("coordinator.aws.secret-key", secretKey),
                    "",
                    property("coordinator.aws.coordinator.ami", coordinatorAmi),
                    property("coordinator.aws.coordinator.keypair", coordinatorKeypair),
                    property("coordinator.aws.coordinator.security-group", coordinatorSecurityGroup),
                    property("coordinator.aws.coordinator.default-instance-type", coordinatorDefaultInstanceType),
                    property("coordinator.aws.coordinator.default-port", coordinatorDefaultPort),
                    "",
                    property("coordinator.aws.agent.ami", agentAmi),
                    property("coordinator.aws.agent.keypair", agentKeypair),
                    property("coordinator.aws.agent.security-group", agentSecurityGroup),
                    property("coordinator.aws.agent.default-instance-type", agentDefaultInstanceType),
                    property("coordinator.aws.agent.default-port", agentDefaultPort),
                    "");

            String configFile = Joiner.on('\n').skipNulls().join(lines);
            out.write(configFile.getBytes(Charsets.UTF_8));

            out.close();

            return byteArrayOutputStream.toByteArray();
        }
        catch (IOException e) {
            throw new RuntimeException("Error creating coordinator config", e);
        }
    }

    private String property(String key, Object value)
    {
        if (value == null) {
            return null;
        }
        return format("%s=%s", key, value);
    }
}
