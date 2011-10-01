package com.proofpoint.galaxy.coordinator;

import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.model.CreateTagsRequest;
import com.amazonaws.services.ec2.model.Instance;
import com.amazonaws.services.ec2.model.Placement;
import com.amazonaws.services.ec2.model.RunInstancesRequest;
import com.amazonaws.services.ec2.model.RunInstancesResult;
import com.amazonaws.services.ec2.model.Tag;
import com.google.common.base.Charsets;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.proofpoint.http.server.HttpServerInfo;
import com.proofpoint.log.Logger;
import com.proofpoint.node.NodeInfo;
import org.apache.commons.codec.binary.Base64;

import javax.inject.Inject;
import java.net.URI;
import java.util.List;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.collect.Lists.newArrayList;
import static java.lang.String.format;

public class AwsProvisioner
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

    @Inject
    public AwsProvisioner(AmazonEC2 ec2Client, NodeInfo nodeInfo, HttpServerInfo httpServerInfo, CoordinatorConfig coordinatorConfig, CoordinatorAwsConfig awsConfig)
    {
        this.ec2Client = checkNotNull(ec2Client, "ec2Client is null");

        checkNotNull(nodeInfo, "nodeInfo is null");
        this.environment = nodeInfo.getEnvironment();

        checkNotNull(httpServerInfo, "httpServerInfo is null");
        coordinatorUri = httpServerInfo.getHttpUri();

        checkNotNull(coordinatorConfig, "coordinatorConfig is null");
        galaxyVersion = coordinatorConfig.getGalaxyVersion();

        checkNotNull(awsConfig, "awsConfig is null");
        awsAgentAmi = awsConfig.getAwsAgentAmi();
        awsAgentKeypair = awsConfig.getAwsAgentKeypair();
        awsAgentSecurityGroup = awsConfig.getAwsAgentSecurityGroup();
        awsAgentDefaultInstanceType = awsConfig.getAwsAgentDefaultInstanceType();
    }

    public List<Ec2Location> provisionAgents(int agentCount, String instanceType, String availabilityZone)
            throws Exception
    {
        if (instanceType == null) {
            instanceType = awsAgentDefaultInstanceType;
        }

        RunInstancesRequest request = new RunInstancesRequest()
                .withImageId(awsAgentAmi)
                .withKeyName(awsAgentKeypair)
                .withSecurityGroups(awsAgentSecurityGroup)
                .withInstanceType(instanceType)
                .withPlacement(new Placement(availabilityZone))
                .withUserData(getUserData())
                .withMinCount(agentCount)
                .withMaxCount(agentCount);

        log.debug("launching instances: %s", request);
        RunInstancesResult result = ec2Client.runInstances(request);
        log.debug("launched instances: %s", result);

        List<Ec2Location> locations = newArrayList();
        List<String> instanceIds = newArrayList();

        for (Instance instance : result.getReservation().getInstances()) {
            String zone = instance.getPlacement().getAvailabilityZone();
            String region = zone.substring(0, zone.length() - 1);
            locations.add(new Ec2Location(region, zone, instance.getInstanceId(), "agent"));
            instanceIds.add(instance.getInstanceId());
        }

        List<Tag> tags = ImmutableList.<Tag>builder()
                .add(new Tag("Name", format("%s-agent", environment)))
                .add(new Tag("role", "agent"))
                .add(new Tag("environment", environment))
                .build();
        createInstanceTagsWithRetry(instanceIds, tags);

        return locations;
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

    private String getUserData()
    {
        return encodeBase64(getRawUserData());
    }

    private String getRawUserData()
    {
        String boundary = "===============884613ba9e744d0c851955611107553e==";
        String boundaryLine = "--" + boundary;
        String mimeVersion = "MIME-Version: 1.0";
        String encoding = "Content-Transfer-Encoding: 7bit";
        String contentTypeUrl = "Content-Type: text/x-include-url; charset=\"us-ascii\"";
        String contentTypeText = "Content-Type: text/plain; charset=\"us-ascii\"";
        String attachmentFormat = "Content-Disposition: attachment; filename=\"%s\"";
        String[] lines = {
                "Content-Type: multipart/mixed; boundary=\"" + boundary + "\"", mimeVersion,
                "", boundaryLine,

                contentTypeUrl, mimeVersion, encoding, format(attachmentFormat, "galaxy-part-handler.py"), "",
                "http://s3.amazonaws.com/proofpoint-s3/galaxy-part-handler.py",
                "", boundaryLine,

                contentTypeText, mimeVersion, encoding, format(attachmentFormat, "galaxy-agent.properties"), "",
                format("galaxy.version=%s", galaxyVersion),
                format("agent.coordinator-uri=%s", coordinatorUri),
                format("node.environment=%s", environment),
                "", boundaryLine,

                contentTypeUrl, mimeVersion, encoding, format(attachmentFormat, "galaxy-install.rb"), "",
                "http://s3.amazonaws.com/proofpoint-s3/galaxy-install.rb",
                "", boundaryLine,
        };
        return Joiner.on('\n').join(lines);
    }

    private static String encodeBase64(String s)
    {
        return Base64.encodeBase64String(s.getBytes(Charsets.UTF_8));
    }
}
