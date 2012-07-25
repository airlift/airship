package io.airlift.airship.coordinator.auth;

import javax.inject.Inject;

public class SignatureVerifier
{
    private final AuthorizedKeyStore keyStore;

    @Inject
    public SignatureVerifier(AuthorizedKeyStore keyStore)
    {
        this.keyStore = keyStore;
    }

    public AuthorizedKey verify(Fingerprint fingerprint, byte[] signature, byte[] message)
    {
        AuthorizedKey key = keyStore.get(fingerprint);
        if ((key != null) && (key.getPublicKey().verifySignature(signature, message))) {
            return key;
        }
        return null;
    }
}
