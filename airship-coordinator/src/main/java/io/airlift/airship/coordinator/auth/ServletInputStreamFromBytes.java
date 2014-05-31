package io.airlift.airship.coordinator.auth;

import javax.servlet.ReadListener;
import javax.servlet.ServletInputStream;

import java.io.ByteArrayInputStream;
import java.io.IOException;

public class ServletInputStreamFromBytes
        extends ServletInputStream
{
    private final ByteArrayInputStream stream;

    public ServletInputStreamFromBytes(byte[] bytes)
    {
        this.stream = new ByteArrayInputStream(bytes);
    }

    @Override
    public int read()
            throws IOException
    {
        return stream.read();
    }

    @Override
    public int read(byte[] b)
            throws IOException
    {
        return stream.read(b);
    }

    @Override
    public int read(byte[] b, int off, int len)
            throws IOException
    {
        return stream.read(b, off, len);
    }

    @Override
    public long skip(long n)
            throws IOException
    {
        return stream.skip(n);
    }

    @Override
    public int available()
            throws IOException
    {
        return stream.available();
    }

    @Override
    public void close()
            throws IOException
    {
        stream.close();
    }

    @Override
    public void mark(int readlimit)
    {
        stream.mark(readlimit);
    }

    @Override
    public void reset()
            throws IOException
    {
        stream.reset();
    }

    @Override
    public boolean markSupported()
    {
        return stream.markSupported();
    }

    @Override
    public boolean isFinished()
    {
        return stream.available() == 0;
    }

    @Override
    public boolean isReady()
    {
        return stream.available() > 0;
    }

    @Override
    public void setReadListener(ReadListener readListener)
    {
        throw new UnsupportedOperationException("setReadListener");
    }
}
