package com.proofpoint.galaxy.coordinator;

import com.google.common.base.Preconditions;
import com.ning.http.client.AsyncHttpClient;
import com.ning.http.client.Response;
import com.proofpoint.galaxy.shared.Installation;
import com.proofpoint.galaxy.shared.InstallationRepresentation;
import com.proofpoint.galaxy.shared.SlotStatus;
import com.proofpoint.galaxy.shared.SlotStatusRepresentation;
import com.proofpoint.json.JsonCodec;
import com.proofpoint.log.Logger;

import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response.Status;
import java.util.UUID;

import static com.proofpoint.galaxy.shared.SlotLifecycleState.UNKNOWN;
import static com.proofpoint.galaxy.shared.VersionsUtil.GALAXY_AGENT_VERSION_HEADER;
import static com.proofpoint.galaxy.shared.VersionsUtil.GALAXY_SLOT_VERSION_HEADER;
import static com.proofpoint.json.JsonCodec.jsonCodec;

public class HttpRemoteSlot implements RemoteSlot
{
    private static final Logger log = Logger.get(HttpRemoteSlot.class);
    private static final JsonCodec<InstallationRepresentation> installationCodec = jsonCodec(InstallationRepresentation.class);
    private static final JsonCodec<SlotStatusRepresentation> slotStatusCodec = jsonCodec(SlotStatusRepresentation.class);

    private SlotStatus slotStatus;
    private final AsyncHttpClient httpClient;
    private final HttpRemoteAgent agent;

    public HttpRemoteSlot(SlotStatus slotStatus, AsyncHttpClient httpClient, HttpRemoteAgent agent)
    {
        Preconditions.checkNotNull(slotStatus, "slotStatus is null");
        Preconditions.checkNotNull(httpClient, "httpClient is null");
        Preconditions.checkNotNull(agent, "agent is null");

        this.slotStatus = slotStatus;
        this.httpClient = httpClient;
        this.agent = agent;
    }

    @Override
    public UUID getId()
    {
        return slotStatus.getId();
    }

    @Override
    public SlotStatus status()
    {
        return slotStatus;
    }

    private void updateStatus(SlotStatus slotStatus)
    {
        Preconditions.checkArgument(slotStatus.getId().equals(this.slotStatus.getId()),
                String.format("Agent returned status for slot %s, but the status for slot %s was expected", slotStatus.getId(), this.slotStatus.getId()));
        this.slotStatus = slotStatus;
        if (agent != null) {
            agent.setSlotStatus(slotStatus);
        }
    }

    private SlotStatus setErrorStatus(String statusMessage)
    {
        slotStatus = slotStatus.changeState(UNKNOWN);
        return slotStatus.changeStatusMessage(statusMessage);
    }

    @Override
    public SlotStatus assign(Installation installation)
    {
        try {
            String json = installationCodec.toJson(InstallationRepresentation.from(installation));
            Response response = httpClient.preparePut(slotStatus.getSelf() + "/assignment")
                    .setBody(json)
                    .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON)
                    .setHeader(GALAXY_AGENT_VERSION_HEADER, agent.status().getVersion())
                    .setHeader(GALAXY_SLOT_VERSION_HEADER, slotStatus.getVersion())
                    .execute()
                    .get();

            if (response.getStatusCode() != Status.OK.getStatusCode()) {
                return setErrorStatus(response.getStatusText());
            }
            String responseJson = response.getResponseBody();
            SlotStatusRepresentation slotStatusRepresentation = slotStatusCodec.fromJson(responseJson);
            updateStatus(slotStatusRepresentation.toSlotStatus(slotStatus.getInstanceId()));
            return slotStatus;
        }
        catch (Exception e) {
            log.error(e);
            return setErrorStatus(e.getMessage());
        }
    }

    @Override
    public SlotStatus terminate()
    {
        try {
            Response response = httpClient.prepareDelete(slotStatus.getSelf().toString())
                    .setHeader(GALAXY_AGENT_VERSION_HEADER, agent.status().getVersion())
                    .setHeader(GALAXY_SLOT_VERSION_HEADER, slotStatus.getVersion())
                    .execute()
                    .get();

            if (response.getStatusCode() != Status.OK.getStatusCode()) {
                return setErrorStatus(response.getStatusText());
            }
            String responseJson = response.getResponseBody();
            SlotStatusRepresentation slotStatusRepresentation = slotStatusCodec.fromJson(responseJson);
            updateStatus(slotStatusRepresentation.toSlotStatus(slotStatus.getInstanceId()));
            return slotStatus;
        }
        catch (Exception e) {
            log.error(e);
            return setErrorStatus(e.getMessage());
        }
    }

    @Override
    public SlotStatus start()
    {
        try {
            Response response = httpClient.preparePut(slotStatus.getSelf() + "/lifecycle")
                    .setBody("running")
                    .setHeader(GALAXY_SLOT_VERSION_HEADER, slotStatus.getVersion())
                    .execute()
                    .get();

            if (response.getStatusCode() != Status.OK.getStatusCode()) {
                return setErrorStatus(response.getStatusText());
            }
            String responseJson = response.getResponseBody();
            SlotStatusRepresentation slotStatusRepresentation = slotStatusCodec.fromJson(responseJson);
            updateStatus(slotStatusRepresentation.toSlotStatus(slotStatus.getInstanceId()));
            return slotStatus;
        }
        catch (Exception e) {
            log.error(e);
            return setErrorStatus(e.getMessage());
        }
    }

    @Override
    public SlotStatus restart()
    {
        try {
            Response response = httpClient.preparePut(slotStatus.getSelf() + "/lifecycle")
                    .setBody("restarting")
                    .setHeader(GALAXY_SLOT_VERSION_HEADER, slotStatus.getVersion())
                    .execute()
                    .get();

            if (response.getStatusCode() != Status.OK.getStatusCode()) {
                return setErrorStatus(response.getStatusText());
            }
            String responseJson = response.getResponseBody();
            SlotStatusRepresentation slotStatusRepresentation = slotStatusCodec.fromJson(responseJson);
            updateStatus(slotStatusRepresentation.toSlotStatus(slotStatus.getInstanceId()));
            return slotStatus;
        }
        catch (Exception e) {
            log.error(e);
            return setErrorStatus(e.getMessage());
        }
    }

    @Override
    public SlotStatus stop()
    {
        try {
            Response response = httpClient.preparePut(slotStatus.getSelf() + "/lifecycle")
                    .setBody("stopped")
                    .setHeader(GALAXY_SLOT_VERSION_HEADER, slotStatus.getVersion())
                    .execute()
                    .get();

            if (response.getStatusCode() != Status.OK.getStatusCode()) {
                return setErrorStatus(response.getStatusText());
            }
            String responseJson = response.getResponseBody();
            SlotStatusRepresentation slotStatusRepresentation = slotStatusCodec.fromJson(responseJson);
            updateStatus(slotStatusRepresentation.toSlotStatus(slotStatus.getInstanceId()));
            return slotStatus;
        }
        catch (Exception e) {
            log.error(e);
            return setErrorStatus(e.getMessage());
        }
    }
}
