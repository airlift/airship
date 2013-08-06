package io.airlift.airship.coordinator;

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
            String securityGroup,
            String provisioningScriptsArtifact);

    List<Instance> listAgents();

    List<Instance> provisionAgents(String agentConfig,
            int agentCount,
            String instanceType,
            String availabilityZone,
            String ami,
            String keyPair,
            String securityGroup,
            String provisioningScriptsArtifact);

    void terminateAgents(Iterable<String> instanceIds);
}
