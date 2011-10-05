package com.proofpoint.galaxy.coordinator;

import com.proofpoint.configuration.Config;
import com.proofpoint.configuration.ConfigDescription;

import javax.validation.constraints.NotNull;

public class AwsProvisionerConfig
{
    private String awsAccessKey;
    private String awsSecretKey;
    private String awsAgentAmi;
    private String awsAgentKeypair;
    private String awsAgentSecurityGroup;
    private String awsAgentDefaultInstanceType;

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
}
