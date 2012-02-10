package com.proofpoint.galaxy.coordinator;

import java.util.List;

public interface Provisioner
{
    List<Instance> listCoordinators();

    List<Instance> listAgents();

    List<Instance> provisionAgents(String agentConfig, int agentCount, String instanceType, String availabilityZone)
            throws Exception;

    void terminateAgents(List<String> instanceIds);
}
