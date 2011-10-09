package com.proofpoint.galaxy.coordinator;

import com.google.common.base.Charsets;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMap.Builder;
import com.google.common.collect.Lists;
import com.google.common.io.Resources;
import com.google.inject.Inject;
import com.proofpoint.galaxy.shared.ConfigRepository;
import com.proofpoint.json.JsonCodec;
import com.proofpoint.galaxy.shared.ConfigSpec;
import com.proofpoint.node.NodeInfo;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import static com.proofpoint.json.JsonCodec.mapJsonCodec;

public class SimpleConfigRepository implements ConfigRepository
{
    private static final JsonCodec<Map<String, String>> configCodec = mapJsonCodec(String.class, String.class);
    private final List<URI> configRepositoryBases;
    private final String environment;

    public SimpleConfigRepository(String environment, URI configRepositoryBase, URI... configRepositoryBases)
    {
        this.environment = environment;
        Preconditions.checkNotNull(configRepositoryBase, "configRepositoryBase is null");
        Preconditions.checkNotNull(configRepositoryBases, "configRepositoryBases is null");
        this.configRepositoryBases = ImmutableList.<URI>builder().add(configRepositoryBase).add(configRepositoryBases).build();
    }

    @Inject
    public SimpleConfigRepository(NodeInfo nodeInfo, CoordinatorConfig config)
    {
        Preconditions.checkNotNull(nodeInfo, "nodeInfo is null");
        Preconditions.checkNotNull(config, "config is null");
        environment = nodeInfo.getEnvironment();
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
    public Map<String, URI> getConfigMap(String environment, ConfigSpec configSpec)
    {
        StringBuilder uriBuilder = new StringBuilder();
        uriBuilder.append(this.environment).append('/');
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
