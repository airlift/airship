package com.proofpoint.galaxy.shared;

import com.google.common.base.Preconditions;
import org.codehaus.jackson.annotate.JsonAutoDetect;
import org.codehaus.jackson.annotate.JsonCreator;
import org.codehaus.jackson.annotate.JsonMethod;
import org.codehaus.jackson.annotate.JsonProperty;

@JsonAutoDetect(JsonMethod.NONE)
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

    public Assignment upgradeAssignment(Assignment assignment)
    {
        Preconditions.checkNotNull(assignment, "assignment is null");

        BinarySpec binary = assignment.getBinary();
        if (binaryVersion != null) {
            binary = new BinarySpec(binary.getGroupId(), binary.getArtifactId(), binaryVersion, binary.getPackaging(), binary.getClassifier());
        }
        ConfigSpec config = assignment.getConfig();
        if (configVersion != null) {
            config = new ConfigSpec(config.getComponent(), configVersion, config.getPool());
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
