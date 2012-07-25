package io.airlift.airship.coordinator.auth.ssh;

import java.math.BigInteger;
import java.security.interfaces.RSAPublicKey;
import java.util.Arrays;

public class SshRsaPublicKey
        implements RSAPublicKey
{
    private final byte[] encoded;
    private final BigInteger publicExponent;
    private final BigInteger modulus;

    public SshRsaPublicKey(byte[] encoded)
    {
        this.encoded = encoded;

        SshKeyReader sshKeyReader = new SshKeyReader(encoded);
        if (!sshKeyReader.readString().equals("ssh-rsa")) {
            throw new IllegalArgumentException("Key is not a ssh rsa key");
        }

        publicExponent = sshKeyReader.readBigInteger();
        modulus = sshKeyReader.readBigInteger();

        if (!sshKeyReader.isComplete()) {
            throw new IllegalArgumentException("Invalid ssh dsa key");
        }
    }

    @Override
    public String getAlgorithm()
    {
        return "RSA";
    }

    @Override
    public String getFormat()
    {
        return "ssh-rsa";
    }

    @Override
    public byte[] getEncoded()
    {
        return Arrays.copyOf(encoded, encoded.length);
    }

    @Override
    public BigInteger getModulus()
    {
        return modulus;
    }

    @Override
    public BigInteger getPublicExponent()
    {
        return publicExponent;
    }
}
