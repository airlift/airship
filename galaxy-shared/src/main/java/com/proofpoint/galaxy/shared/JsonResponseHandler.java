package com.proofpoint.galaxy.shared;

import com.google.common.base.Charsets;
import com.google.common.base.Throwables;
import com.google.common.io.CharStreams;
import com.proofpoint.http.client.Request;
import com.proofpoint.http.client.Response;
import com.proofpoint.http.client.ResponseHandler;
import com.proofpoint.json.JsonCodec;

import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ConnectException;
import java.net.URI;

public class JsonResponseHandler<T> implements ResponseHandler<T, RuntimeException>
{
    public static <T> JsonResponseHandler<T> create(JsonCodec<T> slotsCodec)
    {
        return new JsonResponseHandler<T>(slotsCodec);
    }

    private final JsonCodec<T> jsonCodec;

    public JsonResponseHandler(JsonCodec<T> slotsCodec)
    {
        jsonCodec = slotsCodec;
    }

    @Override
    public RuntimeException handleException(Request request, Exception exception)
    {
        if (exception instanceof ConnectException) {
            URI uri = request.getUri();
            try {
                uri = new URI(uri.getScheme(), null, uri.getHost(), uri.getPort(), "/", null, null);
            }
            catch (Exception e) {
            }
            return new RuntimeException("Coordinator refused connection: " + uri);
        }
        return Throwables.propagate(exception);
    }

    @Override
    public T handle(Request request, Response response)
    {
        if (response.getStatusCode() / 100 != 2) {
            throw new RuntimeException(response.getStatusMessage());
        }
        String contentType = response.getHeader("Content-Type");
        if (!"application/json".equals(contentType)) {
            throw new RuntimeException("Expected application/json response from server but got " + contentType);
        }
        try {
            String json = CharStreams.toString(new InputStreamReader(response.getInputStream(), Charsets.UTF_8));
            T value = jsonCodec.fromJson(json);
            return value;
        }
        catch (IOException e) {
            throw new RuntimeException("Error reading response from server");
        }
    }
}
