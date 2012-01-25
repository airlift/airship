package com.proofpoint.galaxy.coordinator;

import com.google.inject.Inject;
import com.proofpoint.galaxy.shared.BinarySpec;
import com.proofpoint.galaxy.shared.ConfigRepository;
import com.proofpoint.galaxy.shared.ConfigSpec;

import java.net.URI;

import static com.google.common.base.Preconditions.checkNotNull;

public class ConfigInBinaryRepository implements ConfigRepository
{
    private final BinaryRepository binaryRepository;
    private final String groupId;

    @Inject
    public ConfigInBinaryRepository(BinaryRepository binaryRepository, CoordinatorConfig config)
    {
        this(binaryRepository, checkNotNull(config, "config is null").getConfigRepositoryGroupId());
    }

    public ConfigInBinaryRepository(BinaryRepository binaryRepository, String groupId)
    {
        checkNotNull(binaryRepository, "binaryRepository is null");
        checkNotNull(groupId, "groupId is null");
        this.binaryRepository = binaryRepository;
        this.groupId = groupId;
    }

    @Override
    public URI getConfigFile(ConfigSpec configSpec)
    {
        return binaryRepository.getBinaryUri(toBinarySpec(configSpec));
    }

    private BinarySpec toBinarySpec(ConfigSpec configSpec)
    {
        String artifactId = configSpec.getComponent() + "-" + configSpec.getPool();
        return new BinarySpec(groupId, artifactId, configSpec.getVersion(), "config", null);
    }
}
