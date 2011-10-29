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
import com.proofpoint.galaxy.shared.BinarySpec;
import com.proofpoint.galaxy.shared.ConfigRepository;
import com.proofpoint.galaxy.shared.ConfigSpec;
import com.proofpoint.http.server.HttpServerInfo;
import com.proofpoint.log.Logger;
import com.proofpoint.node.NodeInfo;
import org.apache.commons.codec.binary.Base64;

import javax.inject.Inject;
import java.net.URI;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.collect.Lists.newArrayList;
import static java.lang.String.format;

public class AwsProvisioner implements Provisioner
{
    private static final Logger log = Logger.get(AwsProvisioner.class);

    private final AmazonEC2 ec2Client;
    private final String environment;
    private final URI coordinatorUri;
    private final String galaxyVersion;
    private final String awsAgentAmi;
    private final String awsAgentKeypair;
    private final String awsAgentSecurityGroup;
    private final String awsAgentDefaultInstanceType;
    private final int awsAgentDefaultPort;
    private final BinaryUrlResolver urlResolver;
    private final Set<String> invalidInstances = Collections.newSetFromMap(new ConcurrentHashMap<String, Boolean>());
    private final ConfigRepository configRepository;

    @Inject
    public AwsProvisioner(AmazonEC2 ec2Client,
            NodeInfo nodeInfo,
            HttpServerInfo httpServerInfo,
            BinaryUrlResolver urlResolver,
            ConfigRepository configRepository,
            CoordinatorConfig coordinatorConfig,
            AwsProvisionerConfig awsProvisionerConfig)
    {
        this.ec2Client = checkNotNull(ec2Client, "ec2Client is null");

        checkNotNull(nodeInfo, "nodeInfo is null");
        this.environment = nodeInfo.getEnvironment();

        checkNotNull(httpServerInfo, "httpServerInfo is null");
        coordinatorUri = httpServerInfo.getHttpUri();

        checkNotNull(coordinatorConfig, "coordinatorConfig is null");
        galaxyVersion = coordinatorConfig.getGalaxyVersion();

        checkNotNull(awsProvisionerConfig, "awsConfig is null");
        awsAgentAmi = awsProvisionerConfig.getAwsAgentAmi();
        awsAgentKeypair = awsProvisionerConfig.getAwsAgentKeypair();
        awsAgentSecurityGroup = awsProvisionerConfig.getAwsAgentSecurityGroup();
        awsAgentDefaultInstanceType = awsProvisionerConfig.getAwsAgentDefaultInstanceType();
        awsAgentDefaultPort = awsProvisionerConfig.getAwsAgentDefaultPort();

        this.urlResolver = checkNotNull(urlResolver, "urlResolver is null");
        this.configRepository = checkNotNull(configRepository, "configRepository is null");

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
                    String zone = instance.getPlacement().getAvailabilityZone();
                    String region = zone.substring(0, zone.length() - 1);

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
                    instances.add(new Instance(region, zone, instance.getInstanceId(), instance.getInstanceType(), uri));
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
            String zone = instance.getPlacement().getAvailabilityZone();
            String region = zone.substring(0, zone.length() - 1);
            instances.add(new Instance(region, zone, instance.getInstanceId(), instance.getInstanceType()));
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

        URI partHandler = urlResolver.resolve(new BinarySpec("com.proofpoint.galaxy", "galaxy-ec2", galaxyVersion, "py", "part-handler"));
        URI installScript = urlResolver.resolve(new BinarySpec("com.proofpoint.galaxy", "galaxy-ec2", galaxyVersion, "rb", "install"));

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

        URI resourcesFile = configRepository.getConfigResource(environment, new ConfigSpec("agent", galaxyVersion, instanceType), "etc/resources.properties");
        if (resourcesFile != null) {
            lines.add(
                    contentTypeUrl, mimeVersion, encoding, format(attachmentFormat, "galaxy-agent-resources.properties"), "",
                    resourcesFile.toString(),
                    "",
                    boundaryLine
            );
        }
        return Joiner.on('\n').join(lines.build());
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
