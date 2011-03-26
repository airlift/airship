package com.proofpoint.galaxy.console;

import com.google.common.base.Throwables;
import com.ning.http.client.AsyncHttpClient;
import com.ning.http.client.Response;
import com.proofpoint.experimental.json.JsonCodec;
import com.proofpoint.experimental.json.JsonCodecBuilder;
import com.proofpoint.galaxy.LifecycleState;
import com.proofpoint.galaxy.SlotStatus;
import com.proofpoint.galaxy.SlotStatusRepresentation;
import com.proofpoint.galaxy.agent.Assignment;
import com.proofpoint.galaxy.agent.AssignmentRepresentation;

import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response.Status;
import java.net.URI;
import java.util.UUID;

public class HttpRemoteSlot implements RemoteSlot
{
    private static final JsonCodec<AssignmentRepresentation> assignmentCodec = new JsonCodecBuilder().build(AssignmentRepresentation.class);
    private static final JsonCodec<SlotStatusRepresentation> slotStatusCodec = new JsonCodecBuilder().build(SlotStatusRepresentation.class);

    private final UUID id;
    private final String name;
    private final URI remoteUri;
    private SlotStatus slotStatus;
    private AsyncHttpClient httpClient;

    public HttpRemoteSlot(SlotStatus slotStatus, AsyncHttpClient httpClient)
    {
        this(slotStatus.getId(), slotStatus.getName(), slotStatus.getSelf(), slotStatus, httpClient);
    }

    public HttpRemoteSlot(UUID id, String name, URI remoteUri, SlotStatus slotStatus, AsyncHttpClient httpClient)
    {
        this.id = id;
        this.name = name;
        this.remoteUri = remoteUri;
        this.slotStatus = slotStatus;
        this.httpClient = httpClient;
    }

    @Override
    public UUID getId()
    {
        return id;
    }

    @Override
    public String getName()
    {
        return name;
    }

    @Override
    public URI getSelf()
    {
        return null;
    }

    @Override
    public void setStatus(SlotStatus slotStatus)
    {
        this.slotStatus = slotStatus;
    }

    @Override
    public SlotStatus assign(final Assignment assignment)
    {
        try {
            String json = assignmentCodec.toJson(AssignmentRepresentation.from(assignment));
            Response response = httpClient.preparePut(remoteUri + "/assignment")
                    .setBody(json)
                    .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON)
                    .execute()
                    .get();

            if (response.getStatusCode() != Status.OK.getStatusCode()) {
                throw new RuntimeException("Assignment Failed with " + response.getStatusCode() + " " + response.getStatusText());
            }
            String responseJson = response.getResponseBody();
            SlotStatusRepresentation slotStatusRepresentation = slotStatusCodec.fromJson(responseJson);
            slotStatus = slotStatusRepresentation.toSlotStatus();
            return slotStatus;
        }
        catch (Exception e) {
            slotStatus = new SlotStatus(id, name, remoteUri, LifecycleState.UNKNOWN);
            throw Throwables.propagate(e);
        }
    }

    @Override
    public SlotStatus clear()
    {
        try {
            Response response = httpClient.prepareDelete(remoteUri + "/assignment")
                    .execute()
                    .get();

            if (response.getStatusCode() != Status.OK.getStatusCode()) {
                throw new RuntimeException("Assignment Failed with " + response.getStatusCode() + " " + response.getStatusText());
            }
            String responseJson = response.getResponseBody();
            SlotStatusRepresentation slotStatusRepresentation = slotStatusCodec.fromJson(responseJson);
            slotStatus = slotStatusRepresentation.toSlotStatus();
            return slotStatus;
        }
        catch (Exception e) {
            slotStatus = new SlotStatus(id, name, remoteUri, LifecycleState.UNKNOWN);
            throw Throwables.propagate(e);
        }
    }

    @Override
    public SlotStatus status()
    {
        return slotStatus;
    }

    @Override
    public SlotStatus start()
    {
        try {
            Response response = httpClient.preparePut(remoteUri + "/lifecycle")
                    .setBody("start")
                    .execute()
                    .get();

            if (response.getStatusCode() != Status.OK.getStatusCode()) {
                throw new RuntimeException("Assignment Failed with " + response.getStatusCode() + " " + response.getStatusText());
            }
            String responseJson = response.getResponseBody();
            SlotStatusRepresentation slotStatusRepresentation = slotStatusCodec.fromJson(responseJson);
            slotStatus = slotStatusRepresentation.toSlotStatus();
            return slotStatus;
        }
        catch (Exception e) {
            slotStatus = new SlotStatus(id, name, remoteUri, LifecycleState.UNKNOWN);
            throw Throwables.propagate(e);
        }
    }

    @Override
    public SlotStatus restart()
    {
        try {
            Response response = httpClient.preparePut(remoteUri + "/lifecycle")
                    .setBody("restart")
                    .execute()
                    .get();

            if (response.getStatusCode() != Status.OK.getStatusCode()) {
                throw new RuntimeException("Assignment Failed with " + response.getStatusCode() + " " + response.getStatusText());
            }
            String responseJson = response.getResponseBody();
            SlotStatusRepresentation slotStatusRepresentation = slotStatusCodec.fromJson(responseJson);
            slotStatus = slotStatusRepresentation.toSlotStatus();
            return slotStatus;
        }
        catch (Exception e) {
            slotStatus = new SlotStatus(id, name, remoteUri, LifecycleState.UNKNOWN);
            throw Throwables.propagate(e);
        }
    }

    @Override
    public SlotStatus stop()
    {
        try {
            Response response = httpClient.preparePut(remoteUri + "/lifecycle")
                    .setBody("stop")
                    .execute()
                    .get();

            if (response.getStatusCode() != Status.OK.getStatusCode()) {
                throw new RuntimeException("Assignment Failed with " + response.getStatusCode() + " " + response.getStatusText());
            }
            String responseJson = response.getResponseBody();
            SlotStatusRepresentation slotStatusRepresentation = slotStatusCodec.fromJson(responseJson);
            slotStatus = slotStatusRepresentation.toSlotStatus();
            return slotStatus;
        }
        catch (Exception e) {
            slotStatus = new SlotStatus(id, name, remoteUri, LifecycleState.UNKNOWN);
            throw Throwables.propagate(e);
        }
    }
}
