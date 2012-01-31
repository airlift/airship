package com.proofpoint.galaxy.shared;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableSet;

import javax.inject.Inject;
import java.net.URI;
import java.util.Set;

import static com.google.common.collect.Sets.newTreeSet;

public class RepositorySet implements Repository
{
    private final Set<Repository> repositories;

    @Inject
    public RepositorySet(Set<Repository> repositories)
    {
        Preconditions.checkNotNull(repositories, "repositories is null");
        this.repositories = ImmutableSet.copyOf(repositories);
    }

    @Override
    public String configShortName(String config)
    {
        if (!config.startsWith("@")) {
            return null;
        }
        String noAtSign = config.substring(1);

        for (Repository repository : repositories) {
            String shortName = repository.configShortName(config);
            if (!config.equals(shortName) && !noAtSign.endsWith(shortName)) {
                return shortName;
            }
        }
        return noAtSign.replaceAll("[:%/ !$]", "_");
    }

    @Override
    public String configResolve(String config)
    {
        Set<String> configs = newTreeSet();
        for (Repository repository : repositories) {
            String resolved = repository.configResolve(config);
            if (resolved != null) {
                configs.add(resolved);
            }
        }

        if (configs.size() > 1) {
            throw new RuntimeException(String.format("Ambiguous config %s matched %s", config, configs));
        }

        if (configs.isEmpty()) {
            return null;
        }

        return configs.iterator().next();
    }

    @Override
    public String configUpgrade(String config, String version)
    {
        Set<String> configs = newTreeSet();
        for (Repository repository : repositories) {
            String resolved = repository.configUpgrade(config, version);
            if (resolved != null) {
                configs.add(resolved);
            }
        }

        if (configs.size() > 1) {
            throw new RuntimeException(String.format("Ambiguous upgrade version %s for config %s matched %s", version, config, configs));
        }

        if (configs.isEmpty()) {
            return null;
        }

        return configs.iterator().next();
    }

    @Override
    public boolean configEqualsIgnoreVersion(String config1, String config2)
    {
        for (Repository repository : repositories) {
            if (repository.configEqualsIgnoreVersion(config1, config2)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public URI configToHttpUri(String config)
    {
        Set<URI> httpUris = newTreeSet();
        for (Repository repository : repositories) {
            URI httpUri = repository.configToHttpUri(config);
            if (httpUri != null) {
                httpUris.add(httpUri);
            }
        }

        if (httpUris.size() > 1) {
            throw new RuntimeException(String.format("Ambiguous config %s found %s", config, httpUris));
        }

        if (httpUris.isEmpty()) {
            return null;
        }

        return httpUris.iterator().next();
    }

    @Override
    public String binaryResolve(String binary)
    {
        Set<String> binaries = newTreeSet();
        for (Repository repository : repositories) {
            String resolved = repository.binaryResolve(binary);
            if (resolved != null) {
                binaries.add(resolved);
            }
        }

        if (binaries.size() > 1) {
            throw new RuntimeException(String.format("Ambiguous binary %s matched %s", binary, binaries));
        }

        if (binaries.isEmpty()) {
            return null;
        }

        return binaries.iterator().next();
    }

    @Override
    public String binaryUpgrade(String binary, String version)
    {
        Set<String> binaries = newTreeSet();
        for (Repository repository : repositories) {
            String resolved = repository.binaryUpgrade(binary, version);
            if (resolved != null) {
                binaries.add(resolved);
            }
        }

        if (binaries.size() > 1) {
            throw new RuntimeException(String.format("Ambiguous upgrade version %s for binary %s matched %s", version, binary, binaries));
        }

        if (binaries.isEmpty()) {
            return null;
        }

        return binaries.iterator().next();
    }

    @Override
    public boolean binaryEqualsIgnoreVersion(String binary1, String binary2)
    {
        for (Repository repository : repositories) {
            if (repository.binaryEqualsIgnoreVersion(binary1, binary2)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public URI binaryToHttpUri(String binary)
    {
        Set<URI> httpUris = newTreeSet();
        for (Repository repository : repositories) {
            URI httpUri = repository.binaryToHttpUri(binary);
            if (httpUri != null) {
                httpUris.add(httpUri);
            }
        }

        if (httpUris.size() > 1) {
            throw new RuntimeException(String.format("Ambiguous binary %s found %s", binary, httpUris));
        }

        if (httpUris.isEmpty()) {
            return null;
        }

        return httpUris.iterator().next();
    }
}
