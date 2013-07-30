package io.airlift.airship.coordinator;

import com.amazonaws.AmazonClientException;
import com.amazonaws.auth.AWSCredentials;
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
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import io.airlift.configuration.ConfigurationFactory;
import io.airlift.airship.shared.MavenCoordinates;
import io.airlift.airship.shared.Repository;
import io.airlift.http.server.HttpServerConfig;
import io.airlift.http.server.HttpServerInfo;
import io.airlift.log.Logger;
import io.airlift.node.NodeInfo;
import org.apache.commons.codec.binary.Base64;

import javax.inject.Inject;
import java.net.URI;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

import static com.google.common.base.Objects.firstNonNull;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.collect.Lists.newArrayList;
import static io.airlift.airship.shared.ConfigUtils.createConfigurationFactory;
import static io.airlift.airship.shared.HttpUriBuilder.uriBuilder;
import static java.lang.String.format;
import static java.util.Collections.addAll;

public class AwsProvisioner implements Provisioner
{
    private static final Logger log = Logger.get(AwsProvisioner.class);
    private static final String DEFAULT_PROVISIONING_SCRIPTS = "io.airlift.airship:airship-ec2:%s";

    private final AWSCredentials awsCredentials;
    private final AmazonEC2 ec2Client;
    private final String environment;
    private final URI coordinatorUri;
    private final String airshipVersion;
    private final String subnetId;
    private List<String> repositories;

    private final String agentDefaultConfig;

    private final String provisioningScriptsArtifact;

    private final String agentAmi;
    private final String agentKeypair;
    private final String agentSecurityGroup;
    private final String agentDefaultInstanceType;

    private final Repository repository;

    private final Set<String> invalidInstances = Collections.newSetFromMap(new ConcurrentHashMap<String, Boolean>());

    @Inject
    public AwsProvisioner(AWSCredentials awsCredentials,
            AmazonEC2 ec2Client,
            NodeInfo nodeInfo,
            HttpServerInfo httpServerInfo,
            Repository repository,
            CoordinatorConfig coordinatorConfig,
            AwsProvisionerConfig awsProvisionerConfig)
    {
        this.awsCredentials = checkNotNull(awsCredentials, "awsCredentials is null");
        this.ec2Client = checkNotNull(ec2Client, "ec2Client is null");

        checkNotNull(nodeInfo, "nodeInfo is null");
        this.environment = nodeInfo.getEnvironment();

        checkNotNull(httpServerInfo, "httpServerInfo is null");
        this.coordinatorUri = httpServerInfo.getHttpUri();

        checkNotNull(coordinatorConfig, "coordinatorConfig is null");
        checkNotNull(awsProvisionerConfig, "awsConfig is null");

        repositories = coordinatorConfig.getRepositories();

        airshipVersion = awsProvisionerConfig.getAirshipVersion();
        agentDefaultConfig = awsProvisionerConfig.getAgentDefaultConfig();

        agentAmi = awsProvisionerConfig.getAwsAgentAmi();
        agentKeypair = awsProvisionerConfig.getAwsAgentKeypair();
        agentSecurityGroup = awsProvisionerConfig.getAwsAgentSecurityGroup();
        agentDefaultInstanceType = awsProvisionerConfig.getAwsAgentDefaultInstanceType();

        provisioningScriptsArtifact = firstNonNull(awsProvisionerConfig.getProvisioningScriptsArtifact(), String.format(DEFAULT_PROVISIONING_SCRIPTS, awsProvisionerConfig.getAirshipVersion()));

        subnetId = awsProvisionerConfig.getAwsSubnetId();
        this.repository = checkNotNull(repository, "repository is null");
    }

    @Override
    public List<Instance> listCoordinators()
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
                if ("coordinator".equals(tags.get("airship:role")) && environment.equals(tags.get("airship:environment"))) {
                    String portTag = tags.get("airship:port");
                    if (portTag == null) {
                        if (invalidInstances.add(instance.getInstanceId())) {
                            log.error("Instance %s does not have a airship:port tag", instance.getInstanceId());
                        }
                        continue;
                    }

                    int port;
                    try {
                        port = Integer.parseInt(portTag);
                    }
                    catch (Exception e) {
                        if (invalidInstances.add(instance.getInstanceId())) {
                            log.error("Instance %s airship:port tag is not a number", instance.getInstanceId());
                        }
                        continue;
                    }

                    URI internalUri = buildInstanceInternalURI(port, instance);
                    URI externalUri = buildInstanceExternalURI(port, instance);
                    instances.add(toInstance(instance, internalUri, externalUri, "agent"));
                    invalidInstances.remove(instance.getInstanceId());
                }
            }
        }
        return instances;
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
                if ("agent".equals(tags.get("airship:role")) && environment.equals(tags.get("airship:environment"))) {
                    String portTag = tags.get("airship:port");
                    if (portTag == null) {
                        if (invalidInstances.add(instance.getInstanceId())) {
                            log.error("Instance %s does not have a airship:port tag", instance.getInstanceId());
                        }
                        continue;
                    }

                    int port;
                    try {
                        port = Integer.parseInt(portTag);
                    }
                    catch (Exception e) {
                        if (invalidInstances.add(instance.getInstanceId())) {
                            log.error("Instance %s airship:port tag is not a number", instance.getInstanceId());
                        }
                        continue;
                    }

                    URI internalUri = buildInstanceInternalURI(port, instance);
                    URI externalUri = buildInstanceExternalURI(port, instance);
                    instances.add(toInstance(instance, internalUri, externalUri, "agent"));
                    invalidInstances.remove(instance.getInstanceId());
                }
            }
        }
        return instances;
    }

    @Override
    public List<Instance> provisionCoordinators(String coordinatorConfigSpec,
            int coordinatorCount,
            String instanceType,
            String availabilityZone,
            String ami,
            String keyPair,
            String securityGroup,
            String subnetId,
            String privateIpAddress,
            String provisioningScriptsArtifact)
    {
        checkNotNull(coordinatorConfigSpec, "coordinatorConfigSpec is null");

        ConfigurationFactory configurationFactory = createConfigurationFactory(repository, coordinatorConfigSpec);
        AwsProvisionerConfig awsProvisionerConfig = configurationFactory.build(AwsProvisionerConfig.class);
        HttpServerConfig httpServerConfig = configurationFactory.build(HttpServerConfig.class);

        if (availabilityZone == null) {
            // todo default availability zone should be the same as the current coordinator
        }
        if (provisioningScriptsArtifact == null) {
            provisioningScriptsArtifact = this.provisioningScriptsArtifact;
        }
        instanceType = (instanceType != null ? instanceType : awsProvisionerConfig.getAwsAgentDefaultInstanceType());
        ami = (ami != null ? ami : awsProvisionerConfig.getAwsCoordinatorAmi());
        keyPair = (keyPair != null ? keyPair : awsProvisionerConfig.getAwsCoordinatorKeypair());
        securityGroup = (securityGroup != null ? securityGroup : awsProvisionerConfig.getAwsCoordinatorSecurityGroup());
        subnetId = (subnetId != null ? subnetId : awsProvisionerConfig.getAwsSubnetId());
        privateIpAddress = (privateIpAddress != null ? privateIpAddress : awsProvisionerConfig.getAwsPrivateIpAddress());

        List<Instance> instances = provisionCoordinator(coordinatorConfigSpec,
                coordinatorCount,
                instanceType,
                availabilityZone,
                ami,
                keyPair,
                securityGroup,
                subnetId,
                privateIpAddress,
                provisioningScriptsArtifact,
                httpServerConfig.getHttpPort(),
                awsProvisionerConfig.getAwsCredentialsFile(),
                repositories);
        return instances;
    }

    public List<Instance> provisionCoordinator(String coordinatorConfig,
            int coordinatorCount,
            String instanceType,
            String availabilityZone,
            String ami,
            String keyPair,
            String securityGroup,
            String subnetId,
            String privateIpAddress,
            String provisioningScriptsArtifact,
            int coordinatorPort,
            String awsCredentialsFile,
            List<String> repositories)
    {
        checkNotNull(coordinatorConfig, "coordinatorConfig is null");
        checkNotNull(instanceType, "instanceType is null");
        checkNotNull(ami, "ami is null");
        checkNotNull(keyPair, "keyPair is null");
        checkNotNull(securityGroup, "securityGroup is null");

        List<BlockDeviceMapping> blockDeviceMappings = ImmutableList.<BlockDeviceMapping>builder()
                .add(new BlockDeviceMapping().withVirtualName("ephemeral0").withDeviceName("/dev/sdb"))
                .add(new BlockDeviceMapping().withVirtualName("ephemeral1").withDeviceName("/dev/sdc"))
                .add(new BlockDeviceMapping().withVirtualName("ephemeral2").withDeviceName("/dev/sdd"))
                .add(new BlockDeviceMapping().withVirtualName("ephemeral3").withDeviceName("/dev/sde"))
                .build();

        RunInstancesRequest request = new RunInstancesRequest()
                .withImageId(ami)
                .withKeyName(keyPair)
                .withSecurityGroups(securityGroup)
                .withInstanceType(instanceType)
                .withUserData(getCoordinatorUserData(instanceType, coordinatorConfig, awsCredentialsFile, provisioningScriptsArtifact, repositories))
                .withBlockDeviceMappings(blockDeviceMappings)
                .withMinCount(coordinatorCount)
                .withMaxCount(coordinatorCount);

        if (availabilityZone != null) {
            request.withPlacement(new Placement(availabilityZone));
        }

        if (subnetId != null) {
            request.withSubnetId(subnetId);

            // We have to clear the SecurityGroups as VPC only accepts SecurityGroupIds (sg-xxxxxxxx)
            request.withSecurityGroups((Collection<String>) null);
            request.withSecurityGroupIds(securityGroup);

            if (privateIpAddress != null) {
                if (coordinatorCount == 1) {
                    request.withPrivateIpAddress(privateIpAddress);
                } else if (coordinatorCount > 1) {
                    throw new RuntimeException("Can only create 1 coordinator when specifying a private IP address.");
                }
            }
        }

        log.debug("launching instances: %s", request);
        RunInstancesResult result = null;
        AmazonClientException exception = null;
        for (int i = 0; i < 5; i++) {
            try {
                result = ec2Client.runInstances(request);
                break;
            }
            catch (AmazonClientException e) {
                exception = e;
                try {
                    Thread.sleep(500);
                }
                catch (InterruptedException interruptedException) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Interrupted waiting for instances to provision", e);
                }
            }
        }
        if (result == null) {
            throw exception;
        }
        log.debug("launched instances: %s", result);

        List<Instance> instances = newArrayList();
        List<String> instanceIds = newArrayList();

        for (com.amazonaws.services.ec2.model.Instance instance : result.getReservation().getInstances()) {
            instances.add(toInstance(instance, null, null, "coordinator"));
            instanceIds.add(instance.getInstanceId());
        }

        List<Tag> tags = ImmutableList.<Tag>builder()
                .add(new Tag("Name", format("airship-%s-coordinator", environment)))
                .add(new Tag("airship:role", "coordinator"))
                .add(new Tag("airship:environment", environment))
                .add(new Tag("airship:port", String.valueOf(coordinatorPort)))
                .build();
        createInstanceTagsWithRetry(instanceIds, tags);

        return instances;
    }

    @Override
    public List<Instance> provisionAgents(String agentConfig,
            int agentCount,
            String instanceType,
            String availabilityZone,
            String ami,
            String keyPair,
            String securityGroup,
            String subnetId,
            String privateIpAddress,
            String provisioningScriptsArtifact)
    {
        if (provisioningScriptsArtifact == null) {
            provisioningScriptsArtifact = this.provisioningScriptsArtifact;
        }
        agentConfig = (agentConfig != null ? agentConfig : agentDefaultConfig);
        instanceType = (instanceType != null ? instanceType : agentDefaultInstanceType);
        ami = (ami != null ? ami : agentAmi);
        keyPair = (keyPair != null ? keyPair : agentKeypair);
        securityGroup = (securityGroup != null ? securityGroup : agentSecurityGroup);
        subnetId = (subnetId != null ? subnetId : this.subnetId);

        agentConfig = agentConfig.replaceAll(Pattern.quote("${instanceType}"), instanceType);

        ConfigurationFactory configurationFactory = createConfigurationFactory(repository, agentConfig);
        HttpServerConfig httpServerConfig = configurationFactory.build(HttpServerConfig.class);

        List<BlockDeviceMapping> blockDeviceMappings = ImmutableList.<BlockDeviceMapping>builder()
                .add(new BlockDeviceMapping().withVirtualName("ephemeral0").withDeviceName("/dev/sdb"))
                .add(new BlockDeviceMapping().withVirtualName("ephemeral1").withDeviceName("/dev/sdc"))
                .add(new BlockDeviceMapping().withVirtualName("ephemeral2").withDeviceName("/dev/sdd"))
                .add(new BlockDeviceMapping().withVirtualName("ephemeral3").withDeviceName("/dev/sde"))
                .build();

        RunInstancesRequest request = new RunInstancesRequest()
                .withImageId(ami)
                .withKeyName(keyPair)
                .withSecurityGroups(securityGroup)
                .withInstanceType(instanceType)
                .withPlacement(new Placement(availabilityZone))
                .withUserData(getAgentUserData(instanceType, agentConfig, provisioningScriptsArtifact, repositories))
                .withBlockDeviceMappings(blockDeviceMappings)
                .withMinCount(agentCount)
                .withMaxCount(agentCount);

        if (subnetId != null) {
            request.withSubnetId(subnetId);

            // We have to clear the SecurityGroups as VPC only accepts SecurityGroupIds (sg-xxxxxxxx)
            request.withSecurityGroups((Collection<String>) null);
            request.withSecurityGroupIds(securityGroup);

            if (privateIpAddress != null) {
                if (agentCount == 1) {
                    request.withPrivateIpAddress(privateIpAddress);
                }
                else if (agentCount > 1) {
                    throw new RuntimeException("Can only create 1 agent when specifying a private IP address.");
                }
            }
        }

        log.debug("launching instances: %s", request);
        RunInstancesResult result = ec2Client.runInstances(request);
        log.debug("launched instances: %s", result);

        List<Instance> instances = newArrayList();
        List<String> instanceIds = newArrayList();

        for (com.amazonaws.services.ec2.model.Instance instance : result.getReservation().getInstances()) {
            instances.add(toInstance(instance, null, null, "agent"));
            instanceIds.add(instance.getInstanceId());
        }

        List<Tag> tags = ImmutableList.<Tag>builder()
                .add(new Tag("Name", format("airship-%s-agent", environment)))
                .add(new Tag("airship:role", "agent"))
                .add(new Tag("airship:environment", environment))
                .add(new Tag("airship:port", String.valueOf(httpServerConfig.getHttpPort())))
                .build();
        createInstanceTagsWithRetry(instanceIds, tags);

        return instances;
    }

    @Override
    public void terminateAgents(Iterable<String> instanceIds)
    {
        ec2Client.terminateInstances(new TerminateInstancesRequest(ImmutableList.copyOf(instanceIds)));
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

    private String getAgentUserData(String instanceType, String agentConfig, String provisioningScriptsArtifact, List<String> repositories)
    {
        return encodeBase64(getRawAgentUserData(instanceType, agentConfig, provisioningScriptsArtifact, repositories));
    }

    @VisibleForTesting
    String getRawAgentUserData(String instanceType, String agentConfig, String provisioningScriptsArtifact, List<String> repositories)
    {
        String boundary = "===============884613ba9e744d0c851955611107553e==";
        String boundaryLine = "--" + boundary;
        String mimeVersion = "MIME-Version: 1.0";
        String encodingText = "Content-Transfer-Encoding: 7bit";
        String contentExecUrl = "Content-Type: text/x-include-url; charset=\"us-ascii\"";
        String contentDownloadUrl = "Content-Type: text/x-url";
        String contentTypeText = "Content-Type: text/plain; charset=\"us-ascii\"";
        String attachmentFormat = "Content-Disposition: attachment; filename=\"%s\"";

        List<String> artifactParts = splitArtifactCoordinates(provisioningScriptsArtifact);
        String scriptGroup = artifactParts.get(0);
        String scriptArtifact = artifactParts.get(1);
        String scriptVersion = artifactParts.get(2);

        URI airshipCli = getRequiredUri(repository, new MavenCoordinates("io.airlift.airship", "airship-cli", airshipVersion, "jar", "executable", null));

        URI partHandler = getRequiredUri(repository, new MavenCoordinates(scriptGroup, scriptArtifact, scriptVersion, "py", "part-handler", null));
        URI coordinatorInstall = getRequiredUri(repository, new MavenCoordinates(scriptGroup, scriptArtifact, scriptVersion, "sh", "install", null));
        URI coordinatorInstallPrep = getRequiredUri(repository, new MavenCoordinates(scriptGroup, scriptArtifact, scriptVersion, "sh", "install-prep", null));

        List<String> lines = newArrayList();
        addAll(lines,
                "Content-Type: multipart/mixed; boundary=\"" + boundary + "\"", mimeVersion,
                "",
                boundaryLine,

                contentExecUrl, mimeVersion, encodingText, format(attachmentFormat, "airship-part-handler.py"), "",
                partHandler.toString(),
                "",
                boundaryLine,

                contentDownloadUrl, mimeVersion, encodingText, format(attachmentFormat, "airship"), "",
                airshipCli.toASCIIString(),
                "",
                boundaryLine,

                contentTypeText, mimeVersion, encodingText, format(attachmentFormat, "installer.properties"), "",
                property("airshipEnvironment", environment),
                property("airshipInstallBinary", "io.airlift.airship:airship-agent:" + airshipVersion),
                property("airshipInstallConfig", agentConfig),
                property("airshipRepositoryUris", Joiner.on(',').join(repositories)),
                property("airshipCoordinatorUri", coordinatorUri),
                "",
                boundaryLine,

                contentDownloadUrl, mimeVersion, encodingText, format(attachmentFormat, "airship-install.sh"), "",
                coordinatorInstall.toASCIIString(),
                "",
                boundaryLine ,

                contentExecUrl, mimeVersion, encodingText, format(attachmentFormat, "airship-install-prep.sh"), "",
                coordinatorInstallPrep.toASCIIString(),
                "",
                boundaryLine
        );

        return Joiner.on('\n').skipNulls().join(lines);
    }

    private List<String> splitArtifactCoordinates(String provisioningScriptsArtifact)
    {
        ImmutableList<String> artifactParts = ImmutableList.copyOf(Splitter.on(':').split(provisioningScriptsArtifact));
        checkArgument(artifactParts.size() == 3, "Invalid provisioning scripts artifact: %s", provisioningScriptsArtifact);
        return artifactParts;
    }

    private String getCoordinatorUserData(String instanceType, String coordinatorConfig, String awsCredentialsFile, String provisioningScriptsArtifact, List<String> repositories)
    {
        return encodeBase64(getRawCoordinatorUserData(instanceType, coordinatorConfig, awsCredentialsFile, provisioningScriptsArtifact, repositories));
    }

    @VisibleForTesting
    String getRawCoordinatorUserData(String instanceType, String coordinatorConfig, String awsCredentialsFile, String provisioningScriptsArtifact, List<String> repositories)
    {
        String boundary = "===============884613ba9e744d0c851955611107553e==";
        String boundaryLine = "--" + boundary;
        String mimeVersion = "MIME-Version: 1.0";
        String encodingText = "Content-Transfer-Encoding: 7bit";
        String contentExecUrl = "Content-Type: text/x-include-url; charset=\"us-ascii\"";
        String contentDownloadUrl = "Content-Type: text/x-url";
        String contentTypeText = "Content-Type: text/plain; charset=\"us-ascii\"";
        String attachmentFormat = "Content-Disposition: attachment; filename=\"%s\"";

        List<String> artifactParts = splitArtifactCoordinates(provisioningScriptsArtifact);
        String scriptGroup = artifactParts.get(0);
        String scriptArtifact = artifactParts.get(1);
        String scriptVersion = artifactParts.get(2);

        URI airshipCli = getRequiredUri(repository, new MavenCoordinates("io.airlift.airship", "airship-cli", airshipVersion, "jar", "executable", null));

        URI partHandler = getRequiredUri(repository, new MavenCoordinates(scriptGroup, scriptArtifact, scriptVersion, "py", "part-handler", null));
        URI coordinatorInstall = getRequiredUri(repository, new MavenCoordinates(scriptGroup, scriptArtifact, scriptVersion, "sh", "install", null));
        URI coordinatorInstallPrep = getRequiredUri(repository, new MavenCoordinates(scriptGroup, scriptArtifact, scriptVersion, "sh", "install-prep", null));

        List<String> lines = newArrayList();
        addAll(lines,
                "Content-Type: multipart/mixed; boundary=\"" + boundary + "\"", mimeVersion,
                "",
                boundaryLine,

                contentExecUrl, mimeVersion, encodingText, format(attachmentFormat, "airship-part-handler.py"), "",
                partHandler.toString(),
                "",
                boundaryLine,

                contentDownloadUrl, mimeVersion, encodingText, format(attachmentFormat, "airship"), "",
                airshipCli.toASCIIString(),
                "",
                boundaryLine,

                contentTypeText, mimeVersion, encodingText, format(attachmentFormat, "aws-credentials.properties"), "",
                property("aws.access-key", awsCredentials.getAWSAccessKeyId()),
                property("aws.secret-key", awsCredentials.getAWSSecretKey()),
                "",
                boundaryLine,

                contentTypeText, mimeVersion, encodingText, format(attachmentFormat, "installer.properties"), "",
                property("airshipEnvironment", environment),
                property("airshipInstallBinary", "io.airlift.airship:airship-coordinator:" + airshipVersion),
                property("airshipInstallConfig", coordinatorConfig),
                property("airshipRepositoryUris", Joiner.on(',').join(repositories)),
                property("airshipAwsCredentialsFile", awsCredentialsFile),
                "",
                boundaryLine,

                contentDownloadUrl, mimeVersion, encodingText, format(attachmentFormat, "airship-install.sh"), "",
                coordinatorInstall.toASCIIString(),
                "",
                boundaryLine ,

                contentExecUrl, mimeVersion, encodingText, format(attachmentFormat, "airship-install-prep.sh"), "",
                coordinatorInstallPrep.toASCIIString(),
                "",
                boundaryLine

        );

        return Joiner.on('\n').skipNulls().join(lines);
    }

    private static URI getRequiredUri(Repository repository, MavenCoordinates mavenCoordinates)
    {
        URI uri = repository.binaryToHttpUri(mavenCoordinates.toGAV());
        checkArgument(uri != null, "Could not find %s in repository %s", mavenCoordinates.toGAV(), repository);
        return uri;
    }

    public static Instance toInstance(com.amazonaws.services.ec2.model.Instance instance, URI internalUri, URI externalUri, String role)
    {
        return new Instance(instance.getInstanceId(), instance.getInstanceType(), getLocation(instance, role), internalUri, externalUri);
    }

    public static String getLocation(com.amazonaws.services.ec2.model.Instance instance, String role)
    {
        String zone = instance.getPlacement().getAvailabilityZone();
        String region = zone.substring(0, zone.length() - 1);
        return Joiner.on('/').join("", "ec2", region, zone, instance.getInstanceId(), role);
    }

    private static String encodeBase64(String s)
    {
        return Base64.encodeBase64String(s.getBytes(Charsets.UTF_8));
    }

    public static URI buildInstanceInternalURI(int port, com.amazonaws.services.ec2.model.Instance instance) {
        URI internalUri = null;
        if (instance.getPrivateIpAddress() != null) {
            internalUri = uriBuilder().scheme("http").host(instance.getPrivateIpAddress()).port(port).build();
        }
        return internalUri;
    }

    public static URI buildInstanceExternalURI(int port, com.amazonaws.services.ec2.model.Instance instance) {
        URI externalUri;
        if (instance.getPublicDnsName() != null && !instance.getPublicDnsName().isEmpty()) {
            System.out.println(format("\n\nPublic DNS Name: '%s'", instance.getPublicDnsName()));
            externalUri = uriBuilder().scheme("http").host(instance.getPublicDnsName()).port(port).build();
        } else {
            // VPC Instances don't have external addresses so we use the internal address instead.
            externalUri = buildInstanceInternalURI(port, instance);
        }
        return externalUri;
    }

    private Map<String, String> toMap(List<Tag> tags)
    {
        ImmutableMap.Builder<String, String> builder = ImmutableMap.builder();
        for (Tag tag : tags) {
            builder.put(tag.getKey(), tag.getValue());
        }
        return builder.build();
    }

    private String property(String key, Object value)
    {
        if (value == null) {
            return null;
        }
        return format("%s=%s", key, value);
    }
}
