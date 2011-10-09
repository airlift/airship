package com.proofpoint.galaxy.configuration;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.Files;
import com.google.common.io.InputSupplier;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.proofpoint.galaxy.shared.ConfigRepository;
import com.proofpoint.galaxy.shared.ConfigSpec;
import com.proofpoint.galaxy.shared.FileUtils;
import com.proofpoint.http.server.HttpServerInfo;
import com.proofpoint.log.Logger;
import com.proofpoint.units.Duration;
import org.eclipse.jgit.api.Git;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.inject.Inject;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import static com.google.common.collect.Maps.newLinkedHashMap;
import static com.proofpoint.galaxy.shared.FileUtils.newFile;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

public class GitConfigurationRepository implements ConfigRepository
{
    private static final Logger log = Logger.get(GitConfigurationRepository.class);
    private final URI blobUri;
    private final ScheduledExecutorService executorService;
    private final File localRepository;
    private final Duration refreshInterval;
    private final RepositoryUpdater repositoryUpdater;

    @Inject
    public GitConfigurationRepository(GitConfigurationRepositoryConfig config, HttpServerInfo httpServerInfo)
            throws Exception
    {
        Preconditions.checkNotNull(config, "config is null");

        localRepository = new File(config.getLocalConfigRepo()).getAbsoluteFile();
        if (localRepository.isDirectory()) {
            FileUtils.deleteDirectoryContents(localRepository);
        }

        log.info("Local repository  is %s", localRepository.getAbsolutePath());

        if (httpServerInfo.getHttpsUri() != null) {
            blobUri = httpServerInfo.getHttpsUri().resolve("/v1/config/");
        }
        else {
            blobUri = httpServerInfo.getHttpUri().resolve("/v1/config/");
        }

        refreshInterval = config.getRefreshInterval();
        executorService = Executors.newScheduledThreadPool(1, new ThreadFactoryBuilder().setNameFormat("GitConfigRepository-%s").setDaemon(true).build());
        repositoryUpdater = new RepositoryUpdater(new File(config.getLocalConfigRepo()), config.getRemoteUri());
    }

    @PostConstruct
    public void start()
    {
        if (executorService != null) {
            executorService.scheduleWithFixedDelay(repositoryUpdater, 0, (long) refreshInterval.toMillis(), MILLISECONDS);
        }
    }

    @PreDestroy
    public void stop()
    {
        if (executorService != null) {
            executorService.shutdownNow();
        }
    }

    @Override
    public Map<String, URI> getConfigMap(String environment, ConfigSpec configSpec)
    {
        return getConfigMap(environment, configSpec.getComponent(), configSpec.getVersion(), configSpec.getPool());
    }

    public Map<String, URI> getConfigMap(String environment, String type, String version, String pool)
    {
        if (localRepository == null) {
            return null;
        }

        File configDir = newFile(localRepository, environment, type, pool, version);
        if (!configDir.isDirectory()) {
            return null;
        }

        Map<String, URI> configMap = newLinkedHashMap();
        URI baseConfigUri = blobUri.resolve(String.format("%s/%s/%s/%s/", environment, type, pool, version));
        for (String path : getConfigMap("", configDir)) {
            configMap.put(path, baseConfigUri.resolve(path));
        }
        for (String path : getConfigMap("", newFile(localRepository, environment, "defaults"))) {
            if (!configMap.containsKey(path)) {
                configMap.put(path, baseConfigUri.resolve(path));
            }
        }
        return ImmutableMap.copyOf(configMap);
    }

    private List<String> getConfigMap(String basePath, File dir)
    {
        ImmutableList.Builder<String> builder = ImmutableList.builder();
        for (File file : FileUtils.listFiles(dir)) {
            String path = basePath + file.getName();
            if (file.isDirectory()) {
                builder.addAll(getConfigMap(path + "/", file));
            } else if (!file.getName().startsWith("galaxy-")) {
                builder.add(path);
            }
        }

        return builder.build();
    }

    @Override
    public InputSupplier<? extends InputStream> getConfigFile(String environment, ConfigSpec configSpec, String path)
    {
        return getConfigFile(environment, configSpec.getComponent(), configSpec.getVersion(), configSpec.getPool(), path);
    }

    public InputSupplier<FileInputStream> getConfigFile(String environment, String type, String version, String pool, String path)
    {
        if (localRepository == null) {
            return null;
        }

        File file = newFile(localRepository, environment, type, pool, version, path);
        if (!file.canRead()) {
            file = newFile(newFile(localRepository, environment, "defaults"), path);
        }
        return Files.newInputStreamSupplier(file);
    }

    private static class RepositoryUpdater implements Runnable
    {
        private final File localRepository;
        private final String remoteGitUri;
        private boolean failed;

        public RepositoryUpdater(File localRepository, String remoteUri)
        {
            this.localRepository = localRepository;
            this.remoteGitUri = remoteUri;
        }

        @Override
        public void run()
        {
            try {
                if (!new File(localRepository, ".git").isDirectory()) {
                    // clone
                    Git.cloneRepository()
                            .setURI(remoteGitUri)
                            .setDirectory(localRepository)
                            .setTimeout(60) // timeout is in seconds
                            .call();
                }
                else {
                    Git git = Git.open(localRepository);
                    git.fetch()
                            .setRemote("origin")
                            .setTimeout(60) // timeout is in seconds
                            .call();
                    git.merge()
                            .include(git.getRepository().getRef("refs/remotes/origin/master"))
                            .call();
                }

                failed = false;
            }
            catch (Exception e) {
                if (!failed) {
                    failed = true;
                    log.error("Unable to fetch git config repository", e);
                }
            }
        }
    }
}
