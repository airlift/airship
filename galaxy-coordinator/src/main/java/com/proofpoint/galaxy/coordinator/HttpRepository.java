package com.proofpoint.galaxy.coordinator;

import com.google.common.base.Function;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.io.ByteProcessor;
import com.google.common.io.ByteStreams;
import com.google.common.io.InputSupplier;
import com.google.common.io.Resources;
import com.proofpoint.galaxy.shared.Repository;

import javax.annotation.Nullable;
import javax.inject.Inject;
import java.io.InputStream;
import java.net.URI;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.google.common.collect.Sets.newHashSet;
import static com.proofpoint.galaxy.shared.MavenCoordinates.DEFAULT_BINARY_PACKAGING;
import static com.proofpoint.galaxy.shared.MavenCoordinates.DEFAULT_CONFIG_PACKAGING;

public class HttpRepository implements Repository
{
    private final List<URI> baseUris;
    private final Pattern configShortNamePattern;
    private final Pattern configVersionPattern;
    private final Pattern binaryVersionPattern;

    @Inject
    public HttpRepository(CoordinatorConfig config)
    {
        this(
                Lists.transform(config.getHttpRepoBases(), new Function<String, URI>()
                {
                    @Override
                    public URI apply(@Nullable String uri)
                    {
                        return URI.create(uri);
                    }
                }),
                null,
                config.getHttpRepoConfigVersionPattern(),
                config.getHttpRepoBinaryVersionPattern());
    }

    public HttpRepository(Iterable<URI> baseUris, String configShortNamePattern, String configVersionPattern, String binaryVersionPattern)
    {
        Preconditions.checkNotNull(baseUris, "baseUris is null");

        this.baseUris = ImmutableList.copyOf(baseUris);

        if (configShortNamePattern != null) {
            this.configShortNamePattern = Pattern.compile(configShortNamePattern);
            Preconditions.checkArgument(this.configShortNamePattern.matcher("").groupCount() == 1, "configShortNamePattern must have one capturing group");
        }
        else {
            this.configShortNamePattern = null;
        }

        if (configVersionPattern != null) {
            this.configVersionPattern = Pattern.compile(configVersionPattern);
            Preconditions.checkArgument(this.configVersionPattern.matcher("").groupCount() >= 1, "configVersionPattern must have at least one capturing group");
        }
        else {
            this.configVersionPattern = null;
        }

        if (binaryVersionPattern != null) {
            this.binaryVersionPattern = Pattern.compile(binaryVersionPattern);
            Preconditions.checkArgument(this.configVersionPattern.matcher("").groupCount() >= 1, "configVersionPattern must have at least one capturing group");
        }
        else {
            this.binaryVersionPattern = null;
        }
    }

    @Override
    public String configShortName(String config)
    {
        Matcher matcher = configShortNamePattern.matcher(config);
        if (matcher.matches()) {
            return matcher.group(1);
        }

        return config;
    }

    @Override
    public String configResolve(String config)
    {
        if (!config.startsWith("@")) {
            return null;
        }
        config = config.substring(1);

        URI uri = toHttpUri(config, DEFAULT_CONFIG_PACKAGING);
        if (uri != null) {
            return "@" + config;
        }
        return null;
    }

    @Override
    public String configUpgrade(String config, String version)
    {
        if (!config.startsWith("@") || !version.startsWith("@")) {
            return null;
        }
        config = config.substring(1);
        version = version.substring(1);
        String upgrade = upgrade(config, version, configVersionPattern, DEFAULT_CONFIG_PACKAGING);
        if (upgrade != null) {
            return "@" + upgrade;
        }
        return null;
    }

    @Override
    public boolean configEqualsIgnoreVersion(String config1, String config2)
    {
        return config1.startsWith("@") &&
                config2.startsWith("@") &&
                equalsIgnoreVersion(config1, config2, configVersionPattern);
    }

    @Override
    public URI configToHttpUri(String config)
    {
        if (!config.startsWith("@")) {
            return null;
        }
        config = config.substring(1);
        return toHttpUri(config, DEFAULT_CONFIG_PACKAGING);
    }

    @Override
    public String binaryResolve(String binary)
    {
        URI uri = toHttpUri(binary, DEFAULT_BINARY_PACKAGING);
        if (uri != null) {
            return binary;
        }
        return null;
    }

    @Override
    public String binaryUpgrade(String binary, String version)
    {
        return upgrade(binary, version, binaryVersionPattern, DEFAULT_BINARY_PACKAGING);
    }

    @Override
    public boolean binaryEqualsIgnoreVersion(String binary1, String binary2)
    {
        return equalsIgnoreVersion(binary1, binary2, binaryVersionPattern);
    }

    @Override
    public URI binaryToHttpUri(String binary)
    {
        return toHttpUri(binary, DEFAULT_BINARY_PACKAGING);
    }

    private String upgrade(String path, String version, Pattern versionPattern, String defaultPackaging)
    {
        // try to replace version in existing config
        if (versionPattern != null) {
            String newConfig = upgradePath(path, version, versionPattern);
            if (newConfig != null && toHttpUri(newConfig, defaultPackaging) != null) {
                return newConfig;
            }
        }

        // version pattern did not match, so check if new version is an absolute uri
        URI uri = toHttpUri(version, defaultPackaging);
        if (uri != null) {
            return version;
        }
        return null;
    }

    private boolean equalsIgnoreVersion(String path1, String path2, Pattern versionPattern)
    {
        String path1NoVersion;
        Matcher matcher1 = versionPattern.matcher(path1);
        if (!matcher1.find()) {
            return false;
        }
        path1NoVersion = matcher1.replaceAll("");

        String path2NoVersion;
        Matcher matcher2 = versionPattern.matcher(path2);
        if (!matcher2.find()) {
            return false;
        }
        path2NoVersion = matcher2.replaceAll("");

        return path1NoVersion.equals(path2NoVersion);
    }

    private URI toHttpUri(String path, String defaultExtension)
    {
        try {
            URI uri = URI.create(path);
            if (uri.isAbsolute()) {
                if (isValidLocation(uri)) {
                    return uri;
                }
                else {
                    uri = uri.resolve("." + defaultExtension);
                    if (isValidLocation(uri)) {
                        return uri;
                    }
                    else {
                        return null;
                    }
                }
            }
        }
        catch (Exception ignored) {
        }

        Set<URI> uris = newHashSet();
        for (URI baseUri : baseUris) {
            try {
                URI uri = append(baseUri, path);
                if (isValidLocation(uri)) {
                    uris.add(uri);
                }
                else {
                    uri = uri.resolve("." + defaultExtension);
                    if (isValidLocation(uri)) {
                        uris.add(uri);
                    }
                }
            }
            catch (Exception ignored) {
            }
        }

        if (uris.size() > 1) {
            throw new RuntimeException("Ambiguous spec " + path + "  matched " + uris);
        }

        if (uris.isEmpty()) {
            return null;
        }

        return uris.iterator().next();
    }

    private URI append(URI baseUri, String path)
    {
        String base = baseUri.toASCIIString();
        if (!base.endsWith("/")) {
            base = "/";
        }
        return URI.create(base + path);
    }

    private boolean isValidLocation(URI uri)
    {
        try {
            InputSupplier<InputStream> inputSupplier = Resources.newInputStreamSupplier(uri.toURL());
            ByteStreams.readBytes(inputSupplier, new ByteProcessor<Void>()
            {
                private int count;

                public boolean processBytes(byte[] buffer, int offset, int length)
                {
                    count += length;
                    // make sure we got at least 10 bytes
                    return count < 10;
                }

                public Void getResult()
                {
                    return null;
                }
            });
            return true;
        }
        catch (Exception ignored) {
        }
        return false;
    }

    public static String upgradePath(String spec, String version, Pattern versionPattern)
    {
        Matcher matcher = versionPattern.matcher(spec);

        StringBuilder out = new StringBuilder();
        int end = 0;
        while (matcher.find()) {
            for (int group = 0; group < matcher.groupCount(); group++) {
                out.append(spec.substring(end, matcher.start(group + 1))).append(version);
                end = matcher.end(group + 1);
            }
        }
        // no matches
        if (end == 0) {
            return null;
        }
        out.append(spec.substring(end));
        return out.toString();
    }
}
