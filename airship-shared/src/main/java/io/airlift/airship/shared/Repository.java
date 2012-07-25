package io.airlift.airship.shared;

import java.net.URI;

public interface Repository
{
    String configShortName(String config);

    String configRelativize(String config);
    String configResolve(String config);
    String configUpgrade(String config, String version);
    boolean configEqualsIgnoreVersion(String config1, String config2);
    URI configToHttpUri(String config);

    String binaryRelativize(String config);
    String binaryResolve(String binary);
    String binaryUpgrade(String binary, String version);
    boolean binaryEqualsIgnoreVersion(String binary1, String binary2);
    URI binaryToHttpUri(String binary);
}
