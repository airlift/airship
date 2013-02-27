package io.airlift.airship.shared;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.Preconditions;

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
            Preconditions.checkArgument(binary != null, "Can not upgrade binary " + assignment.getBinary() + " to " + binaryVersion);
        } else{
            Preconditions.checkArgument(repository.binaryToHttpUri(assignment.getBinary()) != null, "Can not locate existing binary " + assignment.getBinary() + " for upgrade");            
        }
        
        String config = assignment.getConfig();
        if (configVersion != null) {
            config = repository.configUpgrade(config, configVersion);
            Preconditions.checkArgument(config != null, "Can not upgrade config " + assignment.getConfig() + " to " + configVersion);
        } else {
            Preconditions.checkArgument(repository.configToHttpUri(assignment.getConfig()) != null, "Can not locate existing config " + assignment.getConfig() + " for upgrade");
        }

        return new Assignment(binary, config);
    }

    @Override
    public String toString()
    {
        final StringBuilder sb = new StringBuilder();
        sb.append("UpgradeRepresentation");
        sb.append("{binaryVersion='").append(binaryVersion).append('\'');
        sb.append(", configVersion='").append(configVersion).append('\'');
        sb.append('}');
        return sb.toString();
    }
}
