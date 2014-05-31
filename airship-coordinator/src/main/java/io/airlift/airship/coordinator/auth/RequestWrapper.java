package io.airlift.airship.coordinator.auth;

import com.google.common.io.ByteStreams;

import javax.servlet.ServletInputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;

class RequestWrapper
        extends HttpServletRequestWrapper
{
    private final byte[] requestBody;
    private final ServletInputStream inputStream;

    public RequestWrapper(HttpServletRequest request)
            throws IOException
    {
        super(request);
        requestBody = ByteStreams.toByteArray(request.getInputStream());
        inputStream = new ServletInputStreamFromInputStream(new ByteArrayInputStream(requestBody));
    }

    public byte[] getRequestBody()
    {
        return requestBody;
    }

    @Override
    public ServletInputStream getInputStream()
            throws IOException
    {
        return inputStream;
    }

    @Override
    public BufferedReader getReader()
            throws IOException
    {
        throw new UnsupportedOperationException("getReader not implemented");
    }
}
