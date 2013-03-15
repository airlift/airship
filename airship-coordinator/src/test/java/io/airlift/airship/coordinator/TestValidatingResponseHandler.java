package io.airlift.airship.coordinator;

import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ListMultimap;
import com.google.common.io.CountingInputStream;
import com.google.common.net.HttpHeaders;
import com.google.common.net.MediaType;
import io.airlift.airship.shared.AgentStatusRepresentation;
import io.airlift.http.client.HttpStatus;
import io.airlift.http.client.Request;
import io.airlift.http.client.Response;
import io.airlift.json.JsonCodec;
import org.testng.annotations.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.List;

import static io.airlift.airship.coordinator.ValidatingResponseHandler.validate;
import static io.airlift.http.client.JsonResponseHandler.createJsonResponseHandler;
import static io.airlift.http.client.Request.Builder.prepareGet;

public class TestValidatingResponseHandler
{
    @Test(expectedExceptions = IllegalArgumentException.class, expectedExceptionsMessageRegExp = ".*agentId=may not be null.*")
    public void testValidate()
    {
        JsonCodec<AgentStatusRepresentation> agentCodec = JsonCodec.jsonCodec(AgentStatusRepresentation.class);
        Request request = prepareGet().setUri(URI.create("http://localhost/")).build();
        Response response = fakeJsonResponse("{}");
        validate(createJsonResponseHandler(agentCodec)).handle(request, response);
    }

    private static Response fakeJsonResponse(String json)
    {
        InputStream input = new ByteArrayInputStream(json.getBytes(Charsets.UTF_8));
        final CountingInputStream countingInputStream = new CountingInputStream(input);
        return new Response()
        {
            @Override
            public int getStatusCode()
            {
                return HttpStatus.OK.code();
            }

            @Override
            public String getStatusMessage()
            {
                return HttpStatus.OK.reason();
            }

            @Override
            public String getHeader(String name)
            {
                List<String> list = getHeaders().get(name);
                return list.isEmpty() ? null : list.get(0);
            }

            @Override
            public ListMultimap<String, String> getHeaders()
            {
                return ImmutableListMultimap.<String, String>builder()
                        .put(HttpHeaders.CONTENT_TYPE, MediaType.JSON_UTF_8.toString())
                        .build();
            }

            @Override
            public long getBytesRead()
            {
                return countingInputStream.getCount();
            }

            @Override
            public InputStream getInputStream()
                    throws IOException
            {
                return countingInputStream;
            }
        };
    }
}
