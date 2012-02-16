package com.proofpoint.galaxy.shared;

import com.google.common.base.Charsets;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ListMultimap;
import com.google.common.io.CharStreams;
import com.proofpoint.galaxy.shared.FullJsonResponseHandler.JsonResponse;
import com.proofpoint.http.client.Request;
import com.proofpoint.http.client.Response;
import com.proofpoint.http.client.ResponseHandler;
import com.proofpoint.json.JsonCodec;

import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ConnectException;
import java.net.URI;
import java.util.List;

public class FullJsonResponseHandler<T> implements ResponseHandler<JsonResponse<T>, RuntimeException>
{
    public static <T> FullJsonResponseHandler<T> create(JsonCodec<T> slotsCodec)
    {
        return new FullJsonResponseHandler<T>(slotsCodec);
    }

    private final JsonCodec<T> jsonCodec;

    public FullJsonResponseHandler(JsonCodec<T> slotsCodec)
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
    public JsonResponse<T> handle(Request request, Response response)
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

            return new JsonResponse<T>(value, response.getStatusCode(), response.getStatusMessage(), response.getHeaders());
        }
        catch (IOException e) {
            throw new RuntimeException("Error reading response from server");
        }
    }

    public static class JsonResponse<T>
    {
        private final T value;
        private final int statusCode;
        private final String statusMessage;
        private final ListMultimap<String, String> headers;

        public JsonResponse(T value, int statusCode, String statusMessage, ListMultimap<String, String> headers)
        {
            //To change body of created methods use File | Settings | File Templates.
            this.value = value;
            this.statusCode = statusCode;
            this.statusMessage = statusMessage;
            this.headers = ImmutableListMultimap.copyOf(headers);
        }

        public T getValue()
        {
            return value;
        }

        public int getStatusCode()
        {
            return statusCode;
        }

        public String getStatusMessage()
        {
            return statusMessage;
        }

        public String getHeader(String name)
        {
            List<String> values = getHeaders().get(name);
            if (values.isEmpty()) {
                return null;
            }
            return values.get(0);
        }

        public ListMultimap<String, String> getHeaders()
        {
            return headers;
        }
    }
}
