package com.proofpoint.galaxy.coordinator;

import com.proofpoint.configuration.Config;
import com.proofpoint.configuration.ConfigDescription;
import com.proofpoint.units.Duration;

import javax.validation.constraints.NotNull;
import java.util.concurrent.TimeUnit;

public class AwsProvisionerConfig
{
    private String awsAccessKey;
    private String awsSecretKey;
    private String awsAgentAmi;
    private String awsAgentKeypair;
    private String awsAgentSecurityGroup;
    private String awsAgentDefaultInstanceType;
    private int awsAgentDefaultPort = 64002;
    private String awsEndpoint;
    private String s3KeystoreBucket;
    private String s3KeystorePath;
    private Duration s3KeystoreRefreshInterval = new Duration(10, TimeUnit.SECONDS);

    @Config("coordinator.aws.access-key")
    @ConfigDescription("AWS access key for provisioning agents")
    public AwsProvisionerConfig setAwsAccessKey(String awsAccessKey)
    {
        this.awsAccessKey = awsAccessKey;
        return this;
    }

    @NotNull
    public String getAwsAccessKey()
    {
        return awsAccessKey;
    }

    @Config("coordinator.aws.secret-key")
    @ConfigDescription("AWS secret key for provisioning agents")
    public AwsProvisionerConfig setAwsSecretKey(String awsSecretKey)
    {
        this.awsSecretKey = awsSecretKey;
        return this;
    }

    @NotNull
    public String getAwsSecretKey()
    {
        return awsSecretKey;
    }

    @Config("coordinator.aws.agent.ami")
    @ConfigDescription("AWS AMI for provisioned agents")
    public AwsProvisionerConfig setAwsAgentAmi(String awsAgentAmi)
    {
        this.awsAgentAmi = awsAgentAmi;
        return this;
    }

    @NotNull
    public String getAwsAgentAmi()
    {
        return awsAgentAmi;
    }

    @Config("coordinator.aws.agent.keypair")
    @ConfigDescription("AWS keypair for provisioned agents")
    public AwsProvisionerConfig setAwsAgentKeypair(String awsAgentKeypair)
    {
        this.awsAgentKeypair = awsAgentKeypair;
        return this;
    }

    @NotNull
    public String getAwsAgentKeypair()
    {
        return awsAgentKeypair;
    }

    @Config("coordinator.aws.agent.security-group")
    @ConfigDescription("AWS security group for provisioned agents")
    public AwsProvisionerConfig setAwsAgentSecurityGroup(String awsAgentSecurityGroup)
    {
        this.awsAgentSecurityGroup = awsAgentSecurityGroup;
        return this;
    }

    @NotNull
    public String getAwsAgentSecurityGroup()
    {
        return awsAgentSecurityGroup;
    }

    @Config("coordinator.aws.agent.default-instance-type")
    @ConfigDescription("AWS default instance type for provisioned agents")
    public AwsProvisionerConfig setAwsAgentDefaultInstanceType(String awsAgentDefaultInstanceType)
    {
        this.awsAgentDefaultInstanceType = awsAgentDefaultInstanceType;
        return this;
    }

    @NotNull
    public String getAwsAgentDefaultInstanceType()
    {
        return awsAgentDefaultInstanceType;
    }

    @Config("coordinator.aws.agent.default-port")
    @ConfigDescription("AWS default port for provisioned agents")
    public AwsProvisionerConfig setAwsAgentDefaultPort(int awsAgentDefaultPort)
    {
        this.awsAgentDefaultPort = awsAgentDefaultPort;
        return this;
    }

    @NotNull
    public int getAwsAgentDefaultPort()
    {
        return awsAgentDefaultPort;
    }

    @Config("coordinator.aws.endpoint")
    @ConfigDescription("Override AWS endpoint for talking to ec2")
    public AwsProvisionerConfig setAwsEndpoint(String awsEndpoint)
    {
        this.awsEndpoint = awsEndpoint;
        return this;
    }

    public String getAwsEndpoint()
    {
        return awsEndpoint;
    }

    @NotNull
    public String getS3KeystoreBucket()
    {
        return s3KeystoreBucket;
    }

    @Config("coordinator.aws.s3-keystore.bucket")
    @ConfigDescription("S3 bucket for keystore")
    public AwsProvisionerConfig setS3KeystoreBucket(String s3KeystoreBucket)
    {
        this.s3KeystoreBucket = s3KeystoreBucket;
        return this;
    }

    @NotNull
    public String getS3KeystorePath()
    {
        return s3KeystorePath;
    }

    @Config("coordinator.aws.s3-keystore.path")
    @ConfigDescription("S3 path for keystore")
    public AwsProvisionerConfig setS3KeystorePath(String s3KeystorePath)
    {
        this.s3KeystorePath = s3KeystorePath;
        return this;
    }

    @NotNull
    public Duration getS3KeystoreRefreshInterval()
    {
        return s3KeystoreRefreshInterval;
    }

    @Config("coordinator.aws.s3-keystore.refresh")
    @ConfigDescription("Refresh interval for S3 keystore")
    public AwsProvisionerConfig setS3KeystoreRefreshInterval(Duration s3KeystoreRefreshInterval)
    {
        this.s3KeystoreRefreshInterval = s3KeystoreRefreshInterval;
        return this;
    }
}
