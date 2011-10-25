package com.proofpoint.galaxy.coordinator.auth.ssh;

import java.math.BigInteger;
import java.security.interfaces.DSAParams;
import java.security.interfaces.DSAPublicKey;
import java.security.spec.DSAParameterSpec;
import java.util.Arrays;

public class SshDsaPublicKey
        implements DSAPublicKey
{
    private final byte[] encoded;
    private final DSAParams dsaParams;
    private final BigInteger y;

    public SshDsaPublicKey(byte[] encoded)
    {
        this.encoded = encoded;

        SshKeyReader sshKeyReader = new SshKeyReader(encoded);
        if (!sshKeyReader.readString().equals("ssh-dss")) {
            throw new IllegalArgumentException("Key is not a ssh-dss key");
        }

        dsaParams = new DSAParameterSpec(sshKeyReader.readBigInteger(),
                sshKeyReader.readBigInteger(),
                sshKeyReader.readBigInteger());

        y = sshKeyReader.readBigInteger();

        if (!sshKeyReader.isComplete()) {
            throw new IllegalArgumentException("Invalid ssh dss key");
        }
    }

    @Override
    public String getAlgorithm()
    {
        return "DSA";
    }

    @Override
    public String getFormat()
    {
        return "ssh-dss";
    }

    @Override
    public byte[] getEncoded()
    {
        return Arrays.copyOf(encoded, encoded.length);
    }

    @Override
    public DSAParams getParams()
    {
        return dsaParams;
    }

    @Override
    public BigInteger getY()
    {
        return y;
    }
}
