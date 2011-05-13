package com.proofpoint.galaxy.coordinator;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.ByteStreams;
import com.google.common.io.Files;
import com.google.common.io.InputSupplier;
import com.proofpoint.galaxy.shared.ConfigSpec;
import com.proofpoint.http.server.HttpServerInfo;

import javax.inject.Inject;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.core.StreamingOutput;
import javax.ws.rs.core.UriBuilder;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.util.List;
import java.util.Map;

import static com.proofpoint.galaxy.shared.FileUtils.listFiles;
import static com.proofpoint.galaxy.shared.FileUtils.newFile;

public class LocalConfigRepository implements ConfigRepository
{
    private final File localRepo;
    private final URI baseUri;

    @Inject
    public LocalConfigRepository(CoordinatorConfig coordinatorConfig, HttpServerInfo httpServerInfo)
    {
        if (coordinatorConfig.getLocalConfigRepo() != null) {
            localRepo = new File(coordinatorConfig.getLocalConfigRepo());
            if (httpServerInfo.getHttpsUri() != null) {
                baseUri = httpServerInfo.getHttpsUri().resolve("/v1/config/");
            } else {
                baseUri = httpServerInfo.getHttpUri().resolve("/v1/config/");
            }
        }
        else {
            localRepo = null;
            baseUri = null;
        }
    }

    @Override
    public Map<String, URI> getConfigMap(ConfigSpec configSpec)
    {
        if (localRepo == null) {
            return null;
        }

        String pool = configSpec.getPool();
        if (pool == null) {
            pool = "general";
        }
        File dir = newFile(localRepo, configSpec.getEnvironment(), configSpec.getComponent(), pool, configSpec.getVersion());
        if (!dir.isDirectory()) {
            return null;
        }

        URI configBaseUri = UriBuilder.fromUri(baseUri).path(configSpec.getEnvironment()).path(configSpec.getComponent()).path(pool).path(configSpec.getVersion()).path("/").build();

        ImmutableMap.Builder<String, URI> builder = ImmutableMap.builder();
        for (String path : listDirRecursively(dir, "")) {
            builder.put(path, configBaseUri.resolve(path));
        }
        return builder.build();
    }

    public InputSupplier<FileInputStream> getConfigFile(ConfigSpec configSpec, String path)
    {
        if (localRepo == null) {
            return null;
        }

        File file = newFile(localRepo, configSpec.getEnvironment(), configSpec.getComponent(), configSpec.getPool(), configSpec.getVersion(), path);
        if (!file.canRead()) {
            return null;
        }
        return Files.newInputStreamSupplier(file);
    }

    private static List<String> listDirRecursively(File baseDir, String basePath)
    {
        Preconditions.checkNotNull(baseDir, "baseDir is null");
        Preconditions.checkNotNull(basePath, "basePath is null");

        ImmutableList.Builder<String> builder = ImmutableList.builder();
        for (File file : listFiles(baseDir)) {
            if (file.isDirectory()) {
                builder.addAll(listDirRecursively(file, String.format("%s%s/", basePath, file.getName())));
            }
            else {
                builder.add(basePath + file.getName());
            }
        }

        return builder.build();
    }
}
