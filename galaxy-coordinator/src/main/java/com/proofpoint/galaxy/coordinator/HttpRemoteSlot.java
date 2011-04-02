package com.proofpoint.galaxy.coordinator;

import com.google.common.base.Preconditions;
import com.google.common.base.Throwables;
import com.ning.http.client.AsyncHttpClient;
import com.ning.http.client.Response;
import com.proofpoint.experimental.json.JsonCodec;
import com.proofpoint.experimental.json.JsonCodecBuilder;
import com.proofpoint.galaxy.shared.LifecycleState;
import com.proofpoint.galaxy.shared.SlotStatus;
import com.proofpoint.galaxy.shared.SlotStatusRepresentation;
import com.proofpoint.galaxy.shared.Installation;
import com.proofpoint.galaxy.shared.InstallationRepresentation;

import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response.Status;
import java.util.UUID;

public class HttpRemoteSlot implements RemoteSlot
{
    private static final JsonCodec<InstallationRepresentation> installationCodec = new JsonCodecBuilder().build(InstallationRepresentation.class);
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
        Preconditions.checkArgument(slotStatus.getId() != this.slotStatus.getId(),
                String.format("Agent returned status for slot %s, but the status for slot %s was expected", slotStatus.getId(), this.slotStatus.getId()));
        this.slotStatus = slotStatus;
    }

    @Override
    public SlotStatus assign(Installation installation)
    {
        try {
            String json = installationCodec.toJson(InstallationRepresentation.from(installation));
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
            updateStatus(slotStatusRepresentation.toSlotStatus());
            return slotStatus;
        }
        catch (Exception e) {
            slotStatus = new SlotStatus(slotStatus, LifecycleState.UNKNOWN);
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
            updateStatus(slotStatusRepresentation.toSlotStatus());
            return slotStatus;
        }
        catch (Exception e) {
            slotStatus = new SlotStatus(slotStatus, LifecycleState.UNKNOWN);
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
            updateStatus(slotStatusRepresentation.toSlotStatus());
            return slotStatus;
        }
        catch (Exception e) {
            slotStatus = new SlotStatus(slotStatus, LifecycleState.UNKNOWN);
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
            updateStatus(slotStatusRepresentation.toSlotStatus());
            return slotStatus;
        }
        catch (Exception e) {
            slotStatus = new SlotStatus(slotStatus, LifecycleState.UNKNOWN);
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
            updateStatus(slotStatusRepresentation.toSlotStatus());
            return slotStatus;
        }
        catch (Exception e) {
            slotStatus = new SlotStatus(slotStatus, LifecycleState.UNKNOWN);
            throw Throwables.propagate(e);
        }
    }
}
