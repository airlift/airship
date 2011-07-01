package com.proofpoint.galaxy.coordinator;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.Files;
import com.google.common.io.InputSupplier;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
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
import java.io.InputStream;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import static com.proofpoint.galaxy.shared.FileUtils.newFile;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

public class GitConfigRepository implements ConfigRepository
{
    private static final Logger log = Logger.get(GitConfigRepository.class);
    private final URI blobUri;
    private final ScheduledExecutorService executorService;
    private final File localRepository;
    private final Duration refreshInterval;
    private final RepositoryUpdater repositoryUpdater;

    @Inject
    public GitConfigRepository(GitConfigRepositoryConfig config, HttpServerInfo httpServerInfo)
            throws Exception
    {
        Preconditions.checkNotNull(config, "config is null");

        if (config.getRemoteUri() == null) {
            blobUri = null;
            localRepository = null;
            refreshInterval = null;
            executorService = null;
            repositoryUpdater = null;
        }
        else {
            localRepository = new File(config.getLocalConfigRepo());
            log.info("Local repository  is %s", localRepository.getAbsolutePath());

            if (httpServerInfo.getHttpsUri() != null) {
                blobUri = httpServerInfo.getHttpsUri().resolve("/v1/git/blob/master/");
            }
            else {
                blobUri = httpServerInfo.getHttpUri().resolve("/v1/git/blob/master/");
            }

            refreshInterval = config.getRefreshInterval();
            executorService = Executors.newScheduledThreadPool(1, new ThreadFactoryBuilder().setNameFormat("GitConfigRepository-%s").setDaemon(true).build());
            repositoryUpdater = new RepositoryUpdater(config);
        }
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
    public Map<String, URI> getConfigMap(ConfigSpec configSpec)
    {
        if (localRepository == null) {
            return null;
        }

        String pool = configSpec.getPool();
        if (pool == null) {
            pool = "general";
        }

        File configDir = newFile(localRepository, configSpec.getEnvironment(), configSpec.getComponent(), pool, configSpec.getVersion());
        if (!configDir.isDirectory()) {
            return null;
        }

        ImmutableMap.Builder<String, URI> configMap = ImmutableMap.builder();
        for (String path : getConfigMap(String.format("%s/%s/%s/%s/", configSpec.getEnvironment(), configSpec.getComponent(), pool, configSpec.getVersion()), configDir)) {
            configMap.put(path, blobUri.resolve(path));
        }
        return configMap.build();
    }

    private List<String> getConfigMap(String basePath, File dir)
    {
        ImmutableList.Builder<String> builder = ImmutableList.builder();
        for (File file : FileUtils.listFiles(dir)) {
            String path = basePath + file.getName();
            if (file.isDirectory()) {
                builder.addAll(getConfigMap(path + "/", file));
            } else {
                builder.add(path);
            }
        }

        return builder.build();
    }

    public InputSupplier<? extends InputStream> getBlob(String objectIdString)
    {
        if (localRepository == null) {
            return null;
        }

        File file = newFile(localRepository, objectIdString);
        if (!file.canRead()) {
            return null;
        }

        return Files.newInputStreamSupplier(file);
    }

    private static class RepositoryUpdater implements Runnable
    {
        private final File localRepository;
        private final String remoteGitUri;
        private boolean failed;

        public RepositoryUpdater(GitConfigRepositoryConfig config)
        {
            this.localRepository = new File(config.getLocalConfigRepo());
            this.remoteGitUri = config.getRemoteUri();
        }

        @Override
        public void run()
        {
            try {
                if (!localRepository.isDirectory()) {
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
