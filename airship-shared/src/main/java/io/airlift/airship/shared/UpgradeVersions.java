package io.airlift.airship.shared;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.Objects;
import com.google.common.base.Preconditions;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;

public class UpgradeVersions
{
    private final String binaryVersion;
    private final String configVersion;

    @JsonCreator
    public UpgradeVersions(@JsonProperty("binaryVersion") String binaryVersion, @JsonProperty("configVersion") String configVersion)
    {
        this.binaryVersion = binaryVersion;
        this.configVersion = configVersion;
    }

    @JsonProperty
    public String getBinaryVersion()
    {
        return binaryVersion;
    }

    @JsonProperty
    public String getConfigVersion()
    {
        return configVersion;
    }

    public Assignment upgradeAssignment(Repository repository, Assignment assignment)
    {
        Preconditions.checkNotNull(assignment, "assignment is null");

        String binary = assignment.getBinary();
        if (binaryVersion != null) {
            binary = repository.binaryUpgrade(binary, binaryVersion);
            checkArgument(binary != null, "Can not upgrade binary " + assignment.getBinary() + " to " + binaryVersion);
        }
        else {
            checkArgument(repository.binaryToHttpUri(assignment.getBinary()) != null, "Can not locate existing binary " + assignment.getBinary() + " for upgrade");
        }

        String config = assignment.getConfig();
        if (configVersion != null) {
            config = repository.configUpgrade(config, configVersion);
            checkArgument(config != null, "Can not upgrade config " + assignment.getConfig() + " to " + configVersion);
        }
        else {
            checkArgument(repository.configToHttpUri(assignment.getConfig()) != null, "Can not locate existing config " + assignment.getConfig() + " for upgrade");
        }

        return new Assignment(binary, config);
    }

    public Assignment forceAssignment(Repository repository)
    {
        checkState((binaryVersion != null) && (configVersion != null), "Binary and config must be specified to upgrade missing assignment");

        String binary = repository.binaryResolve(binaryVersion);
        checkArgument(binary != null, "Unknown binary " + binaryVersion);

        String config = repository.configResolve(configVersion);
        checkArgument(config != null, "Unknown config " + configVersion);

        return new Assignment(binary, config);
    }

    @Override
    public String toString()
    {
        return Objects.toStringHelper(this)
                .add("binaryVersion", binaryVersion)
                .add("configVersion", configVersion)
                .toString();
    }
}
