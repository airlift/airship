package com.proofpoint.galaxy.coordinator;

import java.util.List;

public interface Provisioner
{
    List<Ec2Location> listAgents();

    List<Ec2Location> provisionAgents(int agentCount, String instanceType, String availabilityZone)
            throws Exception;
}
