package com.proofpoint.galaxy.coordinator;

import java.util.List;

public interface Provisioner
{
    List<Instance> listCoordinators();

    List<Instance> provisionCoordinators(String coordinatorConfigSpec,
            int coordinatorCount,
            String instanceType,
            String availabilityZone,
            String ami,
            String keyPair,
            String securityGroup);

    List<Instance> listAgents();

    List<Instance> provisionAgents(String agentConfig,
            int agentCount,
            String instanceType,
            String availabilityZone,
            String ami,
            String keyPair,
            String securityGroup);

    void terminateAgents(Iterable<String> instanceIds);
}
