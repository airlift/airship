package io.airlift.airship.coordinator.auth;

import org.apache.commons.codec.DecoderException;

import javax.annotation.concurrent.Immutable;
import java.util.Arrays;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static org.apache.commons.codec.binary.Hex.decodeHex;
import static org.apache.commons.codec.binary.Hex.encodeHexString;

@Immutable
public class Fingerprint
{
    private final byte[] fingerprint;

    public Fingerprint(byte[] fingerprint)
    {
        checkNotNull(fingerprint, "fingerprint is null");
        checkArgument(fingerprint.length == 16, "fingerprint must be 16 bytes");
        this.fingerprint = fingerprint;
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Fingerprint)) {
            return false;
        }
        Fingerprint x = (Fingerprint) o;
        return Arrays.equals(fingerprint, x.fingerprint);
    }

    @Override
    public int hashCode()
    {
        return Arrays.hashCode(fingerprint);
    }

    @Override
    public String toString()
    {
        return encodeHexString(fingerprint);
    }

    public static Fingerprint valueOf(String s)
    {
        checkNotNull(s, "fingerprint string is null");
        try {
            return new Fingerprint(decodeHex(s.toCharArray()));
        }
        catch (DecoderException e) {
            throw new IllegalArgumentException("invalid fingerprint encoding");
        }
    }
}
