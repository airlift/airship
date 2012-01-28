package com.proofpoint.galaxy.shared;

import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class BinarySpec
{
    public static final String BINARY_SPEC_REGEX = "^([^:]+):([^:]+)(?::([^:]+))?(?::([^:]+))?(?::([^:]+))?$";
    public static final Pattern BINARY_SPEC_PATTERN = Pattern.compile(BINARY_SPEC_REGEX);
    public static final String DEFAULT_BINARY_PACKAGING = "tar.gz";

    public static MavenCoordinates parseBinarySpec(String binarySpec)
    {
        Matcher matcher = BINARY_SPEC_PATTERN.matcher(binarySpec);
        if (!matcher.matches()) {
            throw new IllegalArgumentException("Invalid binary spec: " + binarySpec);
        }

        List<String> parts = ImmutableList.copyOf(Splitter.on(':').split(binarySpec));
        if (parts.size() == 5) {
            return createBinarySpec(parts.get(0), parts.get(1), parts.get(4), parts.get(2), parts.get(3));
        } else if (parts.size() == 4) {
            return createBinarySpec(parts.get(0), parts.get(1), parts.get(3), parts.get(2), null);
        } else if (parts.size() == 3) {
            return createBinarySpec(parts.get(0), parts.get(1), parts.get(2), null, null);
        } else if (parts.size() == 2) {
            return createBinarySpec(null, parts.get(0), parts.get(1), null, null);
        } else  {
            throw new IllegalArgumentException("Invalid binary spec: " + binarySpec);
        }
    }

    public static MavenCoordinates createBinarySpec(String groupId, String artifactId, String version)
    {
        return new MavenCoordinates(groupId, artifactId, version, DEFAULT_BINARY_PACKAGING, null, null);
    }

    public static MavenCoordinates createBinarySpec(String groupId, String artifactId, String version, String packaging, String classifier)
    {
        if (packaging == null) {
            packaging = DEFAULT_BINARY_PACKAGING;
        }
        return new MavenCoordinates(groupId, artifactId, version, packaging, classifier, null);
    }

    public static MavenCoordinates createBinarySpec(String groupId, String artifactId, String version, String packaging, String classifier, String fileVersion)
    {
        if (packaging == null) {
            packaging = DEFAULT_BINARY_PACKAGING;
        }
        return new MavenCoordinates(groupId, artifactId, version, packaging, classifier, fileVersion);
    }

    public static String toBinaryGAV(MavenCoordinates binarySpec)
    {
        return MavenCoordinates.toGAV(binarySpec, DEFAULT_BINARY_PACKAGING, false);

    }

    private BinarySpec()
    {
    }
}
