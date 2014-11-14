package io.airlift.airship.coordinator;

import com.google.common.io.ByteSource;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.StreamingOutput;

import java.io.IOException;
import java.io.OutputStream;

class InputSupplierStreamingOutput
        implements StreamingOutput
{
    private final ByteSource byteSource;

    public InputSupplierStreamingOutput(ByteSource byteSource)
    {
        this.byteSource = byteSource;
    }

    public void write(OutputStream output)
            throws IOException, WebApplicationException
    {
        byteSource.copyTo(output);
    }
}
