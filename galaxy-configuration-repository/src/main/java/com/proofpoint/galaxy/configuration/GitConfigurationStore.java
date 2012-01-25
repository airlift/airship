package com.proofpoint.galaxy.configuration;

import com.google.common.base.Preconditions;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.proofpoint.galaxy.shared.FileUtils;
import com.proofpoint.log.Logger;
import com.proofpoint.units.Duration;
import org.eclipse.jgit.api.Git;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.inject.Inject;
import java.io.File;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import static com.proofpoint.galaxy.shared.FileUtils.newFile;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

public class GitConfigurationStore
{
    private static final Logger log = Logger.get(GitConfigurationStore.class);
    private final ScheduledExecutorService executorService;
    private final File localRepository;
    private final Duration refreshInterval;
    private final RepositoryUpdater repositoryUpdater;

    @Inject
    public GitConfigurationStore(GitConfigurationRepositoryConfig config)
            throws Exception
    {
        Preconditions.checkNotNull(config, "config is null");

        localRepository = new File(config.getLocalConfigRepo()).getAbsoluteFile();
        if (localRepository.isDirectory()) {
            FileUtils.deleteDirectoryContents(localRepository);
        }

        log.info("Local repository  is %s", localRepository.getAbsolutePath());

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

    public File getConfigDir(String environment, String type, String version, String pool)
    {
        return newFile(localRepository, environment, type, pool, version);
    }

    public File getConfigFile(String environment, String type, String version, String pool, String path)
    {
        if (localRepository == null) {
            return null;
        }

        File file = newFile(getConfigDir(environment, type, version, pool), path);
        if (!file.canRead()) {
            file = newFile(newFile(localRepository, environment, "defaults"), path);
            if (!file.canRead()) {
                return null;
            }
        }
        return file;
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
