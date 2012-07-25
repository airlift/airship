package io.airlift.airship.coordinator.auth.ssh;

import java.math.BigInteger;
import java.security.interfaces.RSAPrivateKey;

public class PemRsaPrivateKey
        implements RSAPrivateKey
{
    private final String pem;
    private final BigInteger privateExponent;
    private final BigInteger modulus;

    public PemRsaPrivateKey(String pem, BigInteger privateExponent, BigInteger modulus)
    {
        this.pem = pem;
        this.privateExponent = privateExponent;
        this.modulus = modulus;
    }

    @Override
    public String getAlgorithm()
    {
        return "RSA";
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
    public BigInteger getModulus()
    {
        return modulus;
    }

    @Override
    public BigInteger getPrivateExponent()
    {
        return privateExponent;
    }

}
