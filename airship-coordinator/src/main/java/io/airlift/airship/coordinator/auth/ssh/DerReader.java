package com.proofpoint.galaxy.coordinator.auth.ssh;

import com.google.common.io.ByteStreams;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigInteger;

public class DerReader
{
    private final ByteArrayInputStream in;

    public DerReader(byte[] data)
    {
        in = new ByteArrayInputStream(data);
    }

    public BigInteger readBigInteger()
    {
        byte[] data = readEntry(DerType.INTEGER);
        return new BigInteger(1, data);
    }

    public byte[] readEntry(DerType expectedType)
    {
        int type = readNextByte();
        if (type != expectedType.getValue()) {
            throw new IllegalStateException("Next entry is not an " + expectedType);
        }
        int length = readLength();
        return readNextBytes(length);
    }

    public boolean isComplete()
    {
        return in.available() == 0;
    }

    private int readLength()
    {
        int value = readNextByte();

        // single byte (no length)
        if (value < (1 << 7)) {
            return value;
        }

        int numberOfBytes = value & 0x0F;

        int length = 0;
        switch (numberOfBytes) {
            case 4:
                length = readNextByte() | (length << 8);
            case 3:
                length = readNextByte() | (length << 8);
            case 2:
                length = readNextByte() | (length << 8);
            case 1:
                length = readNextByte() | (length << 8);
                break;
            default:
                throw new IllegalArgumentException("Entry length is invalid");
        }
        return length;
    }

    private int readNextByte()
    {
        int next = in.read();
        if (next < 0) {
            throw new IllegalArgumentException("Corruption: expected more bytes");
        }
        return next;
    }

    private byte[] readNextBytes(int length)
    {
        try {
            byte[] data = new byte[length];
            ByteStreams.readFully(in, data);
            return data;
        }
        catch (IOException e) {
            throw new IllegalArgumentException("Corruption: expected more bytes");
        }
    }
}
