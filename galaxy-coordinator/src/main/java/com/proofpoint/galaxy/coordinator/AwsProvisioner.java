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
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.CharStreams;
import com.google.common.io.InputSupplier;
import com.proofpoint.galaxy.shared.Repository;
import com.proofpoint.log.Logger;
import com.proofpoint.node.NodeInfo;
import org.apache.commons.codec.binary.Base64;

import javax.inject.Inject;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.collect.Lists.newArrayList;
import static com.proofpoint.galaxy.shared.BinarySpec.createBinarySpec;
import static com.proofpoint.galaxy.shared.ConfigSpec.createConfigSpec;
import static com.proofpoint.galaxy.shared.ConfigUtils.newConfigEntrySupplier;
import static java.lang.String.format;

public class AwsProvisioner implements Provisioner
{
    private static final Logger log = Logger.get(AwsProvisioner.class);

    private final AmazonEC2 ec2Client;
    private final String environment;
    private final String galaxyVersion;
    private final String awsAgentAmi;
    private final String awsAgentKeypair;
    private final String awsAgentSecurityGroup;
    private final String awsAgentDefaultInstanceType;
    private final int awsAgentDefaultPort;
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

        checkNotNull(awsProvisionerConfig, "awsConfig is null");
        awsAgentAmi = awsProvisionerConfig.getAwsAgentAmi();
        awsAgentKeypair = awsProvisionerConfig.getAwsAgentKeypair();
        awsAgentSecurityGroup = awsProvisionerConfig.getAwsAgentSecurityGroup();
        awsAgentDefaultInstanceType = awsProvisionerConfig.getAwsAgentDefaultInstanceType();
        awsAgentDefaultPort = awsProvisionerConfig.getAwsAgentDefaultPort();

        this.repository = checkNotNull(repository, "repository is null");

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

    @Override
    public List<Instance> provisionAgents(int agentCount, String instanceType, String availabilityZone)
            throws Exception
    {
        if (instanceType == null) {
            instanceType = awsAgentDefaultInstanceType;
        }

        List<BlockDeviceMapping> blockDeviceMappings = ImmutableList.<BlockDeviceMapping>builder()
                .add(new BlockDeviceMapping().withVirtualName("ephemeral0").withDeviceName("/dev/sdb"))
                .add(new BlockDeviceMapping().withVirtualName("ephemeral1").withDeviceName("/dev/sdc"))
                .add(new BlockDeviceMapping().withVirtualName("ephemeral2").withDeviceName("/dev/sdd"))
                .add(new BlockDeviceMapping().withVirtualName("ephemeral3").withDeviceName("/dev/sde"))
                .build();

        RunInstancesRequest request = new RunInstancesRequest()
                .withImageId(awsAgentAmi)
                .withKeyName(awsAgentKeypair)
                .withSecurityGroups(awsAgentSecurityGroup)
                .withInstanceType(instanceType)
                .withPlacement(new Placement(availabilityZone))
                .withUserData(getUserData(instanceType))
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
                .add(new Tag("galaxy:port", String.valueOf(awsAgentDefaultPort)))
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

    private String getUserData(String instanceType)
    {
        return encodeBase64(getRawUserData(instanceType));
    }

    @VisibleForTesting
    String getRawUserData(String instanceType)
    {
        String boundary = "===============884613ba9e744d0c851955611107553e==";
        String boundaryLine = "--" + boundary;
        String mimeVersion = "MIME-Version: 1.0";
        String encoding = "Content-Transfer-Encoding: 7bit";
        String contentTypeUrl = "Content-Type: text/x-include-url; charset=\"us-ascii\"";
        String contentTypeText = "Content-Type: text/plain; charset=\"us-ascii\"";
        String attachmentFormat = "Content-Disposition: attachment; filename=\"%s\"";

        URI partHandler = repository.getUri(createBinarySpec("com.proofpoint.galaxy", "galaxy-ec2", galaxyVersion, "py", "part-handler"));
        URI installScript = repository.getUri(createBinarySpec("com.proofpoint.galaxy", "galaxy-ec2", galaxyVersion, "rb", "install"));

        ImmutableList.Builder<String> lines = ImmutableList.builder();
        lines.add(
                "Content-Type: multipart/mixed; boundary=\"" + boundary + "\"", mimeVersion,
                "",
                boundaryLine,

                contentTypeUrl, mimeVersion, encoding, format(attachmentFormat, "galaxy-part-handler.py"), "",
                partHandler.toString(),
                "",
                boundaryLine,

                contentTypeText, mimeVersion, encoding, format(attachmentFormat, "installer.properties"), "",
                format("galaxy.version=%s", galaxyVersion),
                format("environment=%s", environment),
                "artifacts=galaxy-agent",
                "",
                boundaryLine,

                contentTypeText, mimeVersion, encoding, format(attachmentFormat, "galaxy-agent.properties"), "",
                format("http-server.http.port=%s", awsAgentDefaultPort),
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
                createConfigSpec(configArtifactId, galaxyVersion),
                "etc/resources.properties");
        if (resourcesFile != null) {
            try {
                String resourcesProperties = CharStreams.toString(CharStreams.newReaderSupplier(resourcesFile, Charsets.UTF_8));
                lines.add(
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
        return Joiner.on('\n').join(lines.build());
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
}
