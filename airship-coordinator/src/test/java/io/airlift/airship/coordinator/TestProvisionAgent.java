package io.airlift.airship.coordinator;

import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.services.ec2.AmazonEC2Client;
import com.google.common.base.Charsets;
import com.google.common.base.Throwables;
import com.google.common.io.Files;
import io.airlift.http.server.HttpServerConfig;
import io.airlift.http.server.HttpServerInfo;
import io.airlift.json.JsonCodec;
import io.airlift.node.NodeInfo;
import org.testng.annotations.Test;

import java.io.File;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.google.common.collect.Lists.newArrayList;
import static io.airlift.airship.coordinator.TestingMavenRepository.MOCK_REPO;
import static io.airlift.testing.ValidationAssertions.assertValidates;
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
                .setAwsAgentAmi("ami-27b7744e")
                .setAwsAgentKeypair("keypair")
                .setAwsAgentSecurityGroup("default")
                .setAwsAgentDefaultInstanceType("t1.micro");
        assertValidates(awsProvisionerConfig);

        CoordinatorConfig coordinatorConfig = new CoordinatorConfig()
                .setAirshipVersion("0.7-SNAPSHOT");
        assertValidates(coordinatorConfig);

        BasicAWSCredentials awsCredentials = new BasicAWSCredentials(awsAccessKey, awsSecretKey);
        AmazonEC2Client ec2Client = new AmazonEC2Client(awsCredentials);
        NodeInfo nodeInfo = createTestNodeInfo();

        AwsProvisioner provisioner = new AwsProvisioner(awsCredentials,
                ec2Client,
                nodeInfo,
                new HttpServerInfo(new HttpServerConfig(), nodeInfo),
                MOCK_REPO,
                coordinatorConfig,
                awsProvisionerConfig);

        int agentCount = 2;
        List<Instance> provisioned = provisioner.provisionAgents(null, agentCount, null, null, null, null, null);
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
        return new NodeInfo("test", "foo", nodeId, getTestIp(), null, null, "/test/" + nodeId, "binarySpec", "configSpec");
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
