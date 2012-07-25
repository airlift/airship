package io.airlift.airship.coordinator.auth;

import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import org.apache.commons.codec.binary.Base64;

import java.util.Iterator;
import java.util.NoSuchElementException;

import static com.google.common.base.Preconditions.checkNotNull;
import static org.apache.commons.codec.digest.DigestUtils.md5;

public abstract class PublicKey
{
    private final String encodedKey;
    private final String comment;
    protected final byte[] key;

    protected PublicKey(String encodedKey, String comment)
    {
        this.encodedKey = checkNotNull(encodedKey, "encodedKey is null");
        this.comment = checkNotNull(comment, "comment is null");
        this.key = Base64.decodeBase64(encodedKey);
    }

    public abstract String getType();

    public abstract boolean verifySignature(byte[] signature, byte[] message);

    public Fingerprint getFingerprint()
    {
        return new Fingerprint(md5(key));
    }

    @Override
    public String toString()
    {
        return Joiner.on(' ').join(getType(), encodedKey, comment);
    }

    public static PublicKey valueOf(String key)
    {
        Iterator<String> iter = Splitter.on(' ').limit(3).split(key).iterator();
        try {
            String type = iter.next();
            String encodedKey = iter.next();
            String comment = iter.hasNext() ? iter.next() : "";
            if (type.equals("ssh-rsa")) {
                return new RsaPublicKey(encodedKey, comment);
            }
            if (type.equals("ssh-dss")) {
                return new DsaPublicKey(encodedKey, comment);
            }
            throw new IllegalArgumentException("invalid public key type: " + type);
        }
        catch (NoSuchElementException e) {
            throw new IllegalArgumentException("invalid public key");
        }
    }
}
