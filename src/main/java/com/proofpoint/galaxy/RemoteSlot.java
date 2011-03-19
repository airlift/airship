package com.proofpoint.galaxy;

import com.google.common.base.Throwables;
import com.ning.http.client.AsyncHttpClient;
import com.ning.http.client.Response;
import com.proofpoint.experimental.json.JsonCodec;
import com.proofpoint.experimental.json.JsonCodecBuilder;

import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response.Status;
import java.net.URI;
import java.util.UUID;

public class RemoteSlot implements Slot
{
    private static final JsonCodec<AssignmentRepresentation> assignmentCodec = new JsonCodecBuilder().build(AssignmentRepresentation.class);
    private static final JsonCodec<SlotStatusRepresentation> slotStatusCodec = new JsonCodecBuilder().build(SlotStatusRepresentation.class);

    private final UUID id;
    private final String name;
    private final URI self;
    private SlotStatus slotStatus;
    private AsyncHttpClient httpClient;

    public RemoteSlot(UUID id, String name, URI self, SlotStatus slotStatus)
    {
        this.id = id;
        this.name = name;
        this.self = self;
        this.slotStatus = slotStatus;
        httpClient = new AsyncHttpClient();
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
        return self;
    }

    @Override
    public SlotStatus assign(final Assignment assignment)
    {
        try {
            String json = assignmentCodec.toJson(AssignmentRepresentation.from(assignment));
            Response response = httpClient.preparePut(self + "/assignment")
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
            slotStatus = new SlotStatus(id, name, self, LifecycleState.UNKNOWN);
            throw Throwables.propagate(e);
        }
    }

    @Override
    public SlotStatus clear()
    {
        try {
            Response response = httpClient.prepareDelete(self + "/assignment")
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
            slotStatus = new SlotStatus(id, name, self, LifecycleState.UNKNOWN);
            throw Throwables.propagate(e);
        }
    }

    @Override
    public SlotStatus status()
    {
        try {
            Response response = httpClient.prepareGet(self.toString())
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
            slotStatus = new SlotStatus(id, name, self, LifecycleState.UNKNOWN);
            throw Throwables.propagate(e);
        }

    }

    @Override
    public SlotStatus start()
    {
        try {
            Response response = httpClient.preparePut(self + "/lifecycle")
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
            slotStatus = new SlotStatus(id, name, self, LifecycleState.UNKNOWN);
            throw Throwables.propagate(e);
        }
    }

    @Override
    public SlotStatus restart()
    {
        try {
            Response response = httpClient.preparePut(self + "/lifecycle")
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
            slotStatus = new SlotStatus(id, name, self, LifecycleState.UNKNOWN);
            throw Throwables.propagate(e);
        }
    }

    @Override
    public SlotStatus stop()
    {
        try {
            Response response = httpClient.preparePut(self + "/lifecycle")
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
            slotStatus = new SlotStatus(id, name, self, LifecycleState.UNKNOWN);
            throw Throwables.propagate(e);
        }
    }
}
