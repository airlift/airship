package com.proofpoint.galaxy.coordinator;

import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.services.ec2.AmazonEC2Client;
import com.google.common.base.Charsets;
import com.google.common.base.Throwables;
import com.google.common.io.Files;
import com.proofpoint.http.server.HttpServerConfig;
import com.proofpoint.http.server.HttpServerInfo;
import com.proofpoint.json.JsonCodec;
import com.proofpoint.node.NodeInfo;
import org.testng.annotations.Test;

import java.io.File;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.proofpoint.experimental.testing.ValidationAssertions.assertValidates;
import static org.testng.Assert.assertEquals;

public class TestProvisionAgent
{
    @Test(groups = "aws", parameters = "aws-credentials-file")
    public void testProvisionAgent(String awsCredentialsFile)
            throws Exception
    {
        String credentialsJson = Files.toString(new File(awsCredentialsFile), Charsets.UTF_8);
        Map<String, String> map = JsonCodec.mapJsonCodec(String.class, String.class).fromJson(credentialsJson);
        String awsAccessKey = map.get("access-id");
        String awsSecretKey = map.get("private-key");

        CoordinatorAwsConfig awsConfig = new CoordinatorAwsConfig()
                .setAwsAccessKey(awsAccessKey)
                .setAwsSecretKey(awsSecretKey)
                .setAwsAgentAmi("ami-27b7744e")
                .setAwsAgentKeypair("keypair")
                .setAwsAgentSecurityGroup("default")
                .setAwsAgentDefaultInstanceType("t1.micro");
        assertValidates(awsConfig);

        CoordinatorConfig coordinatorConfig = new CoordinatorConfig()
                .setGalaxyVersion("0.7-SNAPSHOT")
                .setLocalBinaryRepo(
                        "https://oss.sonatype.org/content/repositories/releases/,https://oss.sonatype.org/content/repositories/snapshots,http://10.242.211.107:8000/nexus/content/repositories/proofpoint-dev,http://10.242.211.107:8000/nexus/content/repositories/proofpoint-eng-snapshots")
                .setLocalConfigRepo("git://10.242.211.107/config.git");
        assertValidates(coordinatorConfig);

        BasicAWSCredentials awsCredentials = new BasicAWSCredentials(awsConfig.getAwsAccessKey(), awsConfig.getAwsSecretKey());
        AmazonEC2Client ec2Client = new AmazonEC2Client(awsCredentials);
        NodeInfo nodeInfo = createTestNodeInfo();
        HttpServerInfo httpServerInfo = new HttpServerInfo(new HttpServerConfig(), nodeInfo);
        AwsProvisioner awsProvisioner = new AwsProvisioner(ec2Client, nodeInfo, httpServerInfo, coordinatorConfig, awsConfig);

        int agentCount = 3;
        List<Ec2Location> locations = awsProvisioner.provisionAgents(agentCount, null, null);
        assertEquals(locations.size(), agentCount);

        System.out.println("provisioned instances: " + locations);
    }

    private static NodeInfo createTestNodeInfo()
    {
        String nodeId = UUID.randomUUID().toString();
        return new NodeInfo("testing", "general", nodeId, getTestIp(), "/test/" + nodeId);
    }

    private static InetAddress getTestIp()
    {
        try {
            return InetAddress.getByName("192.0.2.0");
        }
        catch (UnknownHostException e) {
            throw Throwables.propagate(e);
        }
    }
}
