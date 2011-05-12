package com.proofpoint.galaxy.coordinator;

import com.google.common.base.Charsets;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMap.Builder;
import com.google.common.collect.Lists;
import com.google.common.io.Resources;
import com.google.inject.Inject;
import com.proofpoint.json.JsonCodec;
import com.proofpoint.galaxy.shared.ConfigSpec;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import static com.proofpoint.json.JsonCodec.mapJsonCodec;

public class SimpleConfigRepository implements ConfigRepository
{
    private static final JsonCodec<Map<String, String>> configCodec = mapJsonCodec(String.class, String.class);
    private final List<URI> configRepositoryBases;

    public SimpleConfigRepository(URI configRepositoryBase, URI... configRepositoryBases)
    {
        Preconditions.checkNotNull(configRepositoryBase, "configRepositoryBase is null");
        Preconditions.checkNotNull(configRepositoryBases, "configRepositoryBases is null");
        this.configRepositoryBases = ImmutableList.<URI>builder().add(configRepositoryBase).add(configRepositoryBases).build();
    }

    public SimpleConfigRepository(Iterable<URI> configRepositoryBases)
    {
        Preconditions.checkNotNull(configRepositoryBases, "configRepositoryBases is null");
        this.configRepositoryBases = ImmutableList.copyOf(configRepositoryBases);
        Preconditions.checkArgument(!this.configRepositoryBases.isEmpty(), "configRepositoryBases is empty");
    }

    @Inject
    public SimpleConfigRepository(CoordinatorConfig config)
    {
        Preconditions.checkNotNull(config, "config is null");
        ImmutableList.Builder<URI> builder = ImmutableList.builder();
        for (String configRepoBase : config.getConfigRepoBases()) {
            if (!configRepoBase.endsWith("/")) {
                configRepoBase = configRepoBase + "/";
            }
            builder.add(URI.create(configRepoBase));
        }
        configRepositoryBases = builder.build();
    }

    @Override
    public Map<String, URI> getConfigMap(ConfigSpec configSpec)
    {
        StringBuilder uriBuilder = new StringBuilder();
        uriBuilder.append(configSpec.getEnvironment()).append('/');
        uriBuilder.append(configSpec.getComponent()).append('/');
        if (configSpec.getPool() != null) {
            uriBuilder.append(configSpec.getPool()).append('/');
        }
        uriBuilder.append(configSpec.getVersion()).append('/');

        List<URI> checkedUris = Lists.newArrayList();
        Map<String, String> configMap = null;
        URI goodUri = null;
        for (URI configRepositoryBase : configRepositoryBases) {
            try {
                URI configBaseUri = configRepositoryBase.resolve(uriBuilder.toString());
                URI uri = configRepositoryBase.resolve(configBaseUri.resolve("config-map.json"));
                checkedUris.add(uri);

                configMap = configCodec.fromJson(Resources.toString(uri.toURL(), Charsets.UTF_8));

                goodUri = uri;
                break;
            }
            catch (Exception ignored) {
            }
        }
        if (configMap == null) {
            throw new RuntimeException("Unable to load configuration " + configSpec + " from " + checkedUris);
        }

        Builder<String, URI> builder = ImmutableMap.builder();
        for (Entry<String, String> entry : configMap.entrySet()) {
            builder.put(entry.getKey(), goodUri.resolve(entry.getValue()));
        }
        return builder.build();
    }
}
