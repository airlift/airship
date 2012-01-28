package com.proofpoint.galaxy.coordinator;

import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.services.ec2.AmazonEC2Client;
import com.google.common.base.Charsets;
import com.google.common.base.Throwables;
import com.google.common.io.Files;
import com.proofpoint.galaxy.shared.Repository;
import com.proofpoint.galaxy.shared.BinarySpec;
import com.proofpoint.json.JsonCodec;
import com.proofpoint.node.NodeInfo;
import org.testng.annotations.Test;

import java.io.File;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.google.common.collect.Lists.newArrayList;
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

        AwsProvisionerConfig awsProvisionerConfig = new AwsProvisionerConfig()
                .setAwsAccessKey(awsAccessKey)
                .setAwsSecretKey(awsSecretKey)
                .setAwsAgentAmi("ami-27b7744e")
                .setAwsAgentKeypair("keypair")
                .setAwsAgentSecurityGroup("default")
                .setAwsAgentDefaultInstanceType("t1.micro");
        assertValidates(awsProvisionerConfig);

        CoordinatorConfig coordinatorConfig = new CoordinatorConfig()
                .setGalaxyVersion("0.7-SNAPSHOT");
        assertValidates(coordinatorConfig);

        BasicAWSCredentials awsCredentials = new BasicAWSCredentials(awsProvisionerConfig.getAwsAccessKey(), awsProvisionerConfig.getAwsSecretKey());
        AmazonEC2Client ec2Client = new AmazonEC2Client(awsCredentials);
        NodeInfo nodeInfo = createTestNodeInfo();
        Repository repository = new Repository() {
            @Override
            public URI getUri(BinarySpec binarySpec)
            {
                try {
                    return new URI("file", null, "host", 9999, "/" + binarySpec.toGAV(), null, null);
                }
                catch (URISyntaxException e) {
                    throw new AssertionError(e);
                }
            }

            @Override
            public BinarySpec resolve(BinarySpec binarySpec)
            {
                return binarySpec;
            }
        };

        AwsProvisioner provisioner = new AwsProvisioner(ec2Client, nodeInfo, repository, coordinatorConfig, awsProvisionerConfig);

        int agentCount = 2;
        List<Instance> provisioned = provisioner.provisionAgents(agentCount, null, null);
        assertEquals(provisioned.size(), agentCount);
        System.err.println("provisioned instances: " + provisioned);

        List<Instance> running = provisioner.listAgents();
        assertEquals(running.size(), agentCount);
        System.err.println("running agents: " + running);

        List<String> instanceIds = newArrayList();
        for (Instance instance : provisioned) {
            instanceIds.add(instance.getInstanceId());
        }
        provisioner.terminateAgents(instanceIds);
    }

    private static NodeInfo createTestNodeInfo()
    {
        String nodeId = UUID.randomUUID().toString();
        return new NodeInfo("test", "foo", nodeId, getTestIp(), "/test/" + nodeId, "binarySpec", "configSpec");
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
