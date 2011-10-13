package com.proofpoint.galaxy.coordinator;

import com.google.common.base.Preconditions;
import com.ning.http.client.AsyncHttpClient;
import com.ning.http.client.Response;
import com.proofpoint.json.JsonCodec;
import com.proofpoint.galaxy.shared.SlotLifecycleState;
import com.proofpoint.galaxy.shared.SlotStatus;
import com.proofpoint.galaxy.shared.SlotStatusRepresentation;
import com.proofpoint.galaxy.shared.Installation;
import com.proofpoint.galaxy.shared.InstallationRepresentation;
import com.proofpoint.log.Logger;

import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response.Status;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static com.proofpoint.json.JsonCodec.jsonCodec;

public class HttpRemoteSlot implements RemoteSlot
{
    private static final Logger log = Logger.get(HttpRemoteSlot.class);
    private static final JsonCodec<InstallationRepresentation> installationCodec = jsonCodec(InstallationRepresentation.class);
    private static final JsonCodec<SlotStatusRepresentation> slotStatusCodec = jsonCodec(SlotStatusRepresentation.class);

    private final UUID id;
    private final AtomicReference<SlotStatus> slotStatus = new AtomicReference<SlotStatus>();
    private final AsyncHttpClient httpClient;

    public HttpRemoteSlot(SlotStatus slotStatus, AsyncHttpClient httpClient)
    {
        this(slotStatus.getId(), slotStatus, httpClient);
    }

    public HttpRemoteSlot(UUID id, SlotStatus slotStatus, AsyncHttpClient httpClient)
    {
        this.id = id;
        this.slotStatus.set(slotStatus);
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
        Preconditions.checkArgument(slotStatus.getId().equals(this.slotStatus.get().getId()),
                String.format("Agent returned status for slot %s, but the status for slot %s was expected", slotStatus.getId(), this.slotStatus.get().getId()));
        this.slotStatus.set(slotStatus);
    }

    private SlotStatus setErrorStatus(SlotLifecycleState status, String statusMessage)
    {
        SlotStatus currentStatus = slotStatus.get();
        slotStatus.set(currentStatus.updateState(status));
        return currentStatus.updateState(status, statusMessage);
    }

    @Override
    public SlotStatus assign(Installation installation)
    {
        try {
            String json = installationCodec.toJson(InstallationRepresentation.from(installation));
            Response response = httpClient.preparePut(slotStatus.get().getSelf() + "/assignment")
                    .setBody(json)
                    .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON)
                    .execute()
                    .get();

            if (response.getStatusCode() != Status.OK.getStatusCode()) {
                return setErrorStatus(SlotLifecycleState.UNKNOWN, response.getStatusText());
            }
            String responseJson = response.getResponseBody();
            SlotStatusRepresentation slotStatusRepresentation = slotStatusCodec.fromJson(responseJson);
            updateStatus(slotStatusRepresentation.toSlotStatus());
            return slotStatus.get();
        }
        catch (Exception e) {
            log.error(e);
            return setErrorStatus(SlotLifecycleState.UNKNOWN, e.getMessage());
        }
    }

    @Override
    public SlotStatus terminate()
    {
        try {
            Response response = httpClient.prepareDelete(slotStatus.get().getSelf().toString())
                    .execute()
                    .get();

            if (response.getStatusCode() != Status.OK.getStatusCode()) {
                return setErrorStatus(SlotLifecycleState.UNKNOWN, response.getStatusText());
            }
            String responseJson = response.getResponseBody();
            SlotStatusRepresentation slotStatusRepresentation = slotStatusCodec.fromJson(responseJson);
            updateStatus(slotStatusRepresentation.toSlotStatus());
            return slotStatus.get();
        }
        catch (Exception e) {
            log.error(e);
            return setErrorStatus(SlotLifecycleState.UNKNOWN, e.getMessage());
        }
    }

    @Override
    public SlotStatus status()
    {
        return slotStatus.get();
    }

    @Override
    public SlotStatus start()
    {
        try {
            Response response = httpClient.preparePut(slotStatus.get().getSelf() + "/lifecycle")
                    .setBody("running")
                    .execute()
                    .get();

            if (response.getStatusCode() != Status.OK.getStatusCode()) {
                return setErrorStatus(SlotLifecycleState.UNKNOWN, response.getStatusText());
            }
            String responseJson = response.getResponseBody();
            SlotStatusRepresentation slotStatusRepresentation = slotStatusCodec.fromJson(responseJson);
            updateStatus(slotStatusRepresentation.toSlotStatus());
            return slotStatus.get();
        }
        catch (Exception e) {
            log.error(e);
            return setErrorStatus(SlotLifecycleState.UNKNOWN, e.getMessage());
        }
    }

    @Override
    public SlotStatus restart()
    {
        try {
            Response response = httpClient.preparePut(slotStatus.get().getSelf() + "/lifecycle")
                    .setBody("restarting")
                    .execute()
                    .get();

            if (response.getStatusCode() != Status.OK.getStatusCode()) {
                return setErrorStatus(SlotLifecycleState.UNKNOWN, response.getStatusText());
            }
            String responseJson = response.getResponseBody();
            SlotStatusRepresentation slotStatusRepresentation = slotStatusCodec.fromJson(responseJson);
            updateStatus(slotStatusRepresentation.toSlotStatus());
            return slotStatus.get();
        }
        catch (Exception e) {
            log.error(e);
            return setErrorStatus(SlotLifecycleState.UNKNOWN, e.getMessage());
        }
    }

    @Override
    public SlotStatus stop()
    {
        try {
            Response response = httpClient.preparePut(slotStatus.get().getSelf() + "/lifecycle")
                    .setBody("stopped")
                    .execute()
                    .get();

            if (response.getStatusCode() != Status.OK.getStatusCode()) {
                return setErrorStatus(SlotLifecycleState.UNKNOWN, response.getStatusText());
            }
            String responseJson = response.getResponseBody();
            SlotStatusRepresentation slotStatusRepresentation = slotStatusCodec.fromJson(responseJson);
            updateStatus(slotStatusRepresentation.toSlotStatus());
            return slotStatus.get();
        }
        catch (Exception e) {
            log.error(e);
            return setErrorStatus(SlotLifecycleState.UNKNOWN, e.getMessage());
        }
    }
}
