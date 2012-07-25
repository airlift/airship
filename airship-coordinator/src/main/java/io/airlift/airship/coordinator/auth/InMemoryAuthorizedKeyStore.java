package io.airlift.airship.coordinator.auth;

import com.google.common.collect.ImmutableMap;

import java.util.List;
import java.util.Map;

public class InMemoryAuthorizedKeyStore
        implements AuthorizedKeyStore
{
    private final Map<Fingerprint, AuthorizedKey> keys;

    public InMemoryAuthorizedKeyStore(List<AuthorizedKey> keys)
    {
        ImmutableMap.Builder<Fingerprint, AuthorizedKey> builder = ImmutableMap.builder();
        for (AuthorizedKey key : keys) {
            builder.put(key.getPublicKey().getFingerprint(), key);
        }
        this.keys = builder.build();
    }

    @Override
    public AuthorizedKey get(Fingerprint fingerprint)
    {
        return keys.get(fingerprint);
    }
}
