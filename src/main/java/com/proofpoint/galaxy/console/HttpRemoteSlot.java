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
import java.util.UUID;

public class HttpRemoteSlot implements RemoteSlot
{
    private static final JsonCodec<AssignmentRepresentation> assignmentCodec = new JsonCodecBuilder().build(AssignmentRepresentation.class);
    private static final JsonCodec<SlotStatusRepresentation> slotStatusCodec = new JsonCodecBuilder().build(SlotStatusRepresentation.class);

    private final UUID id;
    private SlotStatus slotStatus;
    private AsyncHttpClient httpClient;

    public HttpRemoteSlot(SlotStatus slotStatus, AsyncHttpClient httpClient)
    {
        this(slotStatus.getId(), slotStatus, httpClient);
    }

    public HttpRemoteSlot(UUID id, SlotStatus slotStatus, AsyncHttpClient httpClient)
    {
        this.id = id;
        this.slotStatus = slotStatus;
        this.httpClient = httpClient;
    }

    @Override
    public UUID getId()
    {
        return id;
    }

    @Override
    public void updateStatus(SlotStatus slotStatus)
    {
        this.slotStatus = slotStatus;
    }

    @Override
    public SlotStatus assign(final Assignment assignment)
    {
        try {
            String json = assignmentCodec.toJson(AssignmentRepresentation.from(assignment));
            Response response = httpClient.preparePut(slotStatus.getSelf() + "/assignment")
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
            slotStatus = new SlotStatus(id, slotStatus.getName(), slotStatus.getSelf(), LifecycleState.UNKNOWN);
            throw Throwables.propagate(e);
        }
    }

    @Override
    public SlotStatus clear()
    {
        try {
            Response response = httpClient.prepareDelete(slotStatus.getSelf() + "/assignment")
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
            slotStatus = new SlotStatus(id, slotStatus.getName(), slotStatus.getSelf(), LifecycleState.UNKNOWN);
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
            Response response = httpClient.preparePut(slotStatus.getSelf() + "/lifecycle")
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
            slotStatus = new SlotStatus(id, slotStatus.getName(), slotStatus.getSelf(), LifecycleState.UNKNOWN);
            throw Throwables.propagate(e);
        }
    }

    @Override
    public SlotStatus restart()
    {
        try {
            Response response = httpClient.preparePut(slotStatus.getSelf() + "/lifecycle")
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
            slotStatus = new SlotStatus(id, slotStatus.getName(), slotStatus.getSelf(), LifecycleState.UNKNOWN);
            throw Throwables.propagate(e);
        }
    }

    @Override
    public SlotStatus stop()
    {
        try {
            Response response = httpClient.preparePut(slotStatus.getSelf() + "/lifecycle")
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
            slotStatus = new SlotStatus(id, slotStatus.getName(), slotStatus.getSelf(), LifecycleState.UNKNOWN);
            throw Throwables.propagate(e);
        }
    }
}
