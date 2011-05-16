package com.proofpoint.galaxy.coordinator;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.InputSupplier;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.proofpoint.galaxy.shared.ConfigSpec;
import com.proofpoint.http.server.HttpServerInfo;
import com.proofpoint.log.Logger;
import com.proofpoint.units.Duration;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.InvalidRemoteException;
import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.treewalk.filter.PathFilter;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class GitConfigRepository implements ConfigRepository
{
    private static final Logger log = Logger.get(GitConfigRepository.class);
    private final Repository repository;
    private final URI blobUri;
    private final ScheduledExecutorService executorService;
    private final File localRepository;
    private final Duration refreshInterval;

    @Inject
    public GitConfigRepository(GitConfigRepositoryConfig config, HttpServerInfo httpServerInfo)
            throws Exception
    {
        Preconditions.checkNotNull(config, "config is null");

        if (config.getRemoteUri() == null) {
            repository = null;
            blobUri = null;
            localRepository = null;
            refreshInterval = null;
            executorService = null;
        } else {

            localRepository = new File(config.getLocalConfigRepo());
            log.info("Local repository  is %s", localRepository.getAbsolutePath());

            Git git;
            if (!localRepository.isDirectory()) {
                // clone
                git = Git.cloneRepository()
                        .setURI(config.getRemoteUri())
                        .setBranch("HEAD")
                        .setDirectory(localRepository)
                        .setBare(true)
                        .call();
            }
            else {
                // open
                git = Git.open(localRepository);
            }

            repository = git.getRepository();

            if (httpServerInfo.getHttpsUri() != null) {
                blobUri = httpServerInfo.getHttpsUri().resolve("/v1/git/blob/");
            }
            else {
                blobUri = httpServerInfo.getHttpUri().resolve("/v1/git/blob/");
            }

            refreshInterval = config.getRefreshInterval();
            executorService = Executors.newScheduledThreadPool(1, new ThreadFactoryBuilder().setNameFormat("GitConfigRepository-%s").setDaemon(true).build());
        }
    }

    @PostConstruct
    public void start()
    {
        if (executorService != null) {
            executorService.scheduleWithFixedDelay(new FetchGitConfigRepository(localRepository), 0, (long) refreshInterval.toMillis(), TimeUnit.MILLISECONDS);
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
        if (repository == null) {
            return null;
        }

        try {
            // find HEAD commit id
            Ref headRef = repository.getRef("origin/master");
            AnyObjectId headId = headRef.getObjectId();

            // parse the head commit
            RevWalk revWalk = new RevWalk(repository);
            RevCommit headCommit = revWalk.parseCommit(headId);

            // get the tree id of the head commit
            RevTree headTree = headCommit.getTree();

            // walk the head tree
            TreeWalk treeWalk = new TreeWalk(repository);
            treeWalk.addTree(headTree);
            treeWalk.setRecursive(true);


            String pool = configSpec.getPool();
            if (pool == null) {
                pool = "general";
            }
            String pathPrefix = String.format("%s/%s/%s/%s/", configSpec.getEnvironment(), configSpec.getComponent(), pool, configSpec.getVersion());
            treeWalk.setFilter(PathFilter.create(pathPrefix));

            ImmutableMap.Builder<String, URI> blobs = ImmutableMap.builder();
            while (treeWalk.next()) {
                CanonicalTreeParser ft = treeWalk.getTree(0, CanonicalTreeParser.class);
                String path = ft.getEntryPathString();

                ObjectId objectId = treeWalk.getObjectId(0);
                String subPath = path.substring(pathPrefix.length());
                blobs.put(subPath, blobUri.resolve(objectId.getName()));
            }

            // Did the repo contain the configuration?
            Map<String, URI> configMap = blobs.build();
            if (configMap.isEmpty()) {
                return null;
            }
            else {
                return configMap;
            }
        }
        catch (IOException e) {
            throw new RuntimeException("Error reading get config repository", e);
        }
    }

    public InputSupplier<InputStream> getBlob(String objectIdString)
    {
        if (repository == null) {
            return null;
        }

        final ObjectId objectId;
        try {
            objectId = ObjectId.fromString(objectIdString);
        }
        catch (Exception e) {
            // invalid objectId
            return null;
        }
        if (!repository.hasObject(objectId)) {
            return null;
        }

        return new InputSupplier<InputStream>()
        {
            @Override
            public InputStream getInput()
                    throws IOException
            {
                ObjectLoader objectLoader = repository.open(objectId);
                return objectLoader.openStream();
            }
        };
    }

    private static class FetchGitConfigRepository implements Runnable
    {
        private final File localRepository;

        public FetchGitConfigRepository(File localRepository)
        {
            this.localRepository = localRepository;
        }

        @Override
        public void run()
        {
            try {
                // timeout is in seconds
                Git.open(localRepository).fetch().setTimeout(60).call();
            }
            catch (Exception e) {
                log.error("Unable to fetch git config repository", e);
            }
        }
    }
}
