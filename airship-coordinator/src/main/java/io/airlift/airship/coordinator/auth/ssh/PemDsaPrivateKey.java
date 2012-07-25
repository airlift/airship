package com.proofpoint.galaxy.coordinator.auth.ssh;

import java.math.BigInteger;
import java.security.interfaces.DSAParams;
import java.security.interfaces.DSAPrivateKey;
import java.security.spec.DSAParameterSpec;

public class PemDsaPrivateKey
        implements DSAPrivateKey
{
    private final String pem;
    private final DSAParams dsaParams;
    private final BigInteger x;

    public PemDsaPrivateKey(String pem, BigInteger p, BigInteger q, BigInteger g, BigInteger x)
    {
        this(pem, new DSAParameterSpec(p, q, g), x);
    }

    public PemDsaPrivateKey(String pem, DSAParams dsaParams, BigInteger x)
    {
        this.pem = pem;
        this.dsaParams = dsaParams;
        this.x = x;
    }

    @Override
    public String getAlgorithm()
    {
        return "DSA";
    }

    @Override
    public String getFormat()
    {
        return "pem";
    }

    @Override
    public byte[] getEncoded()
    {
        return pem.getBytes();
    }

    @Override
    public DSAParams getParams()
    {
        return dsaParams;
    }

    @Override
    public BigInteger getX()
    {
        return x;
    }
}
