package com.proofpoint.galaxy.coordinator;

import com.google.inject.Inject;
import com.proofpoint.galaxy.shared.ConfigRepository;
import com.proofpoint.galaxy.shared.ConfigSpec;

import java.net.URI;

import static com.google.common.base.Preconditions.checkNotNull;

public class ConfigInBinaryRepository implements ConfigRepository
{
    private final BinaryRepository binaryRepository;

    @Inject
    public ConfigInBinaryRepository(BinaryRepository binaryRepository)
    {
        checkNotNull(binaryRepository, "binaryRepository is null");
        this.binaryRepository = binaryRepository;
    }

    @Override
    public URI getConfigFile(ConfigSpec configSpec)
    {
        return binaryRepository.getBinaryUri(configSpec);
    }
}
