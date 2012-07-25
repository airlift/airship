package io.airlift.airship.coordinator;

import com.google.common.io.ByteStreams;
import com.google.common.io.InputSupplier;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.StreamingOutput;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

class InputSupplierStreamingOutput implements StreamingOutput
{
    private final InputSupplier<? extends InputStream> inputSupplier;

    public InputSupplierStreamingOutput(InputSupplier<? extends InputStream> inputSupplier)
    {
        this.inputSupplier = inputSupplier;
    }

    public void write(OutputStream output)
            throws IOException, WebApplicationException
    {
        ByteStreams.copy(inputSupplier, output);
    }
}
