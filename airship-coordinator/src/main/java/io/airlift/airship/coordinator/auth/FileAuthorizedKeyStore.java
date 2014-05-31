package io.airlift.airship.coordinator.auth;

import javax.inject.Inject;

import java.io.File;
import java.io.IOException;
import java.util.List;

import static com.google.common.base.Charsets.UTF_8;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.collect.Lists.newArrayList;
import static com.google.common.io.Files.readLines;
import static io.airlift.airship.shared.FileUtils.listFiles;

public class FileAuthorizedKeyStore
        implements AuthorizedKeyStore
{
    private final File authorizedKeysDir;

    @Inject
    public FileAuthorizedKeyStore(FileAuthorizedKeyStoreConfig fileAuthorizedKeyStoreConfig)
    {
        this(new File(checkNotNull(fileAuthorizedKeyStoreConfig, "localProvisionerConfig is null").getAuthorizedKeysDir()));
    }

    public FileAuthorizedKeyStore(File authorizedKeysDir)
    {
        this.authorizedKeysDir = authorizedKeysDir;

        //noinspection ResultOfMethodCallIgnored
        authorizedKeysDir.mkdirs();
        checkArgument(authorizedKeysDir.isDirectory(), "authorizedKeysDir is not a directory");
    }

    @Override
    public AuthorizedKey get(Fingerprint fingerprint)
    {
        // TODO: only reload key files when they actually change
        try {
            return loadKeys().get(fingerprint);
        }
        catch (IOException e) {
            throw new RuntimeException("failed loading authorized keys", e);
        }
    }

    AuthorizedKeyStore loadKeys()
            throws IOException
    {
        List<AuthorizedKey> keys = newArrayList();
        for (File file : listFiles(authorizedKeysDir)) {
            String userId = file.getName();
            for (String line : readLines(file, UTF_8)) {
                line = line.trim();
                if (!line.isEmpty()) {
                    PublicKey key = PublicKey.valueOf(line);
                    keys.add(new AuthorizedKey(userId, key));
                }
            }
        }
        return new InMemoryAuthorizedKeyStore(keys);
    }
}
