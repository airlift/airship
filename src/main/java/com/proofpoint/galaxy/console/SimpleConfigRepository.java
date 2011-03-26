package com.proofpoint.galaxy.console;

import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMap.Builder;
import com.google.common.io.Resources;
import com.google.inject.Inject;
import com.google.inject.TypeLiteral;
import com.proofpoint.experimental.json.JsonCodec;
import com.proofpoint.experimental.json.JsonCodecBuilder;
import com.proofpoint.galaxy.ConfigSpec;

import java.net.URI;
import java.util.Map;
import java.util.Map.Entry;

import static com.proofpoint.galaxy.DeploymentUtils.toURL;

public class SimpleConfigRepository implements ConfigRepository
{
    private static final JsonCodec<Map<String, String>> configCodec = new JsonCodecBuilder().build(new TypeLiteral<Map<String, String>>() {});
    private final URI configRepositoryBase;

    public SimpleConfigRepository(URI configRepositoryBase)
    {
        this.configRepositoryBase = configRepositoryBase;
    }

    @Inject
    public SimpleConfigRepository(ConsoleConfig config)
    {
        configRepositoryBase = URI.create(config.getConfigRepoBase());
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
        URI configBaseUri = configRepositoryBase.resolve(uriBuilder.toString());

        URI uri = configRepositoryBase.resolve(configBaseUri.resolve("config-map.json"));
        Map<String, String> configMap;
        try {
            configMap = configCodec.fromJson(Resources.toString(toURL(uri), Charsets.UTF_8));
        }
        catch (Exception ignored) {
            throw new RuntimeException("Unable to load configuration " + configSpec + " from " + uri);
        }

        Builder<String, URI> builder = ImmutableMap.builder();
        for (Entry<String, String> entry : configMap.entrySet()) {
            builder.put(entry.getKey(), configBaseUri.resolve(entry.getValue()));
        }
        return builder.build();
    }
}
