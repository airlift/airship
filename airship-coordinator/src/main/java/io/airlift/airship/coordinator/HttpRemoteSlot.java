package com.proofpoint.galaxy.coordinator;

import com.google.common.base.Preconditions;
import com.proofpoint.galaxy.shared.Installation;
import com.proofpoint.galaxy.shared.InstallationRepresentation;
import com.proofpoint.galaxy.shared.SlotStatus;
import com.proofpoint.galaxy.shared.SlotStatusRepresentation;
import com.proofpoint.http.client.HttpClient;
import com.proofpoint.http.client.Request;
import com.proofpoint.http.client.RequestBuilder;
import com.proofpoint.json.JsonCodec;
import com.proofpoint.log.Logger;

import javax.ws.rs.core.Response.Status;
import java.util.UUID;

import static com.google.common.base.Charsets.UTF_8;
import static com.google.common.net.HttpHeaders.CONTENT_TYPE;
import static com.proofpoint.galaxy.shared.HttpUriBuilder.uriBuilderFrom;
import static com.proofpoint.galaxy.shared.SlotLifecycleState.UNKNOWN;
import static com.proofpoint.galaxy.shared.VersionsUtil.GALAXY_AGENT_VERSION_HEADER;
import static com.proofpoint.galaxy.shared.VersionsUtil.GALAXY_SLOT_VERSION_HEADER;
import static com.proofpoint.http.client.JsonBodyGenerator.jsonBodyGenerator;
import static com.proofpoint.http.client.JsonResponseHandler.createJsonResponseHandler;
import static com.proofpoint.http.client.StaticBodyGenerator.createStaticBodyGenerator;
import static com.proofpoint.json.JsonCodec.jsonCodec;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;

public class HttpRemoteSlot implements RemoteSlot
{
    private static final Logger log = Logger.get(HttpRemoteSlot.class);
    private static final JsonCodec<InstallationRepresentation> installationCodec = jsonCodec(InstallationRepresentation.class);
    private static final JsonCodec<SlotStatusRepresentation> slotStatusCodec = jsonCodec(SlotStatusRepresentation.class);

    private SlotStatus slotStatus;
    private final HttpClient httpClient;
    private final HttpRemoteAgent agent;

    public HttpRemoteSlot(SlotStatus slotStatus, HttpClient httpClient, HttpRemoteAgent agent)
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
            Request request = RequestBuilder.preparePut()
                    .setUri(uriBuilderFrom(slotStatus.getSelf()).appendPath("assignment").build())
                    .setHeader(CONTENT_TYPE, APPLICATION_JSON)
                    .setHeader(GALAXY_AGENT_VERSION_HEADER, agent.status().getVersion())
                    .setHeader(GALAXY_SLOT_VERSION_HEADER, slotStatus.getVersion())
                    .setBodyGenerator(jsonBodyGenerator(installationCodec, InstallationRepresentation.from(installation)))
                    .build();
            SlotStatusRepresentation slotStatusRepresentation = httpClient.execute(request, createJsonResponseHandler(slotStatusCodec, Status.OK.getStatusCode()));

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
            Request request = RequestBuilder.prepareDelete()
                    .setUri(slotStatus.getSelf())
                    .setHeader(GALAXY_AGENT_VERSION_HEADER, agent.status().getVersion())
                    .setHeader(GALAXY_SLOT_VERSION_HEADER, slotStatus.getVersion())
                    .build();
            SlotStatusRepresentation slotStatusRepresentation = httpClient.execute(request, createJsonResponseHandler(slotStatusCodec, Status.OK.getStatusCode()));

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
            Request request = RequestBuilder.preparePut()
                    .setUri(uriBuilderFrom(slotStatus.getSelf()).appendPath("lifecycle").build())
                    .setHeader(GALAXY_AGENT_VERSION_HEADER, agent.status().getVersion())
                    .setHeader(GALAXY_SLOT_VERSION_HEADER, slotStatus.getVersion())
                    .setBodyGenerator(createStaticBodyGenerator("running", UTF_8))
                    .build();
            SlotStatusRepresentation slotStatusRepresentation = httpClient.execute(request, createJsonResponseHandler(slotStatusCodec, Status.OK.getStatusCode()));

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
            Request request = RequestBuilder.preparePut()
                    .setUri(uriBuilderFrom(slotStatus.getSelf()).appendPath("lifecycle").build())
                    .setHeader(GALAXY_AGENT_VERSION_HEADER, agent.status().getVersion())
                    .setHeader(GALAXY_SLOT_VERSION_HEADER, slotStatus.getVersion())
                    .setBodyGenerator(createStaticBodyGenerator("restarting", UTF_8))
                    .build();
            SlotStatusRepresentation slotStatusRepresentation = httpClient.execute(request, createJsonResponseHandler(slotStatusCodec, Status.OK.getStatusCode()));

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
            Request request = RequestBuilder.preparePut()
                    .setUri(uriBuilderFrom(slotStatus.getSelf()).appendPath("lifecycle").build())
                    .setHeader(GALAXY_AGENT_VERSION_HEADER, agent.status().getVersion())
                    .setHeader(GALAXY_SLOT_VERSION_HEADER, slotStatus.getVersion())
                    .setBodyGenerator(createStaticBodyGenerator("stopped", UTF_8))
                    .build();
            SlotStatusRepresentation slotStatusRepresentation = httpClient.execute(request, createJsonResponseHandler(slotStatusCodec, Status.OK.getStatusCode()));

            updateStatus(slotStatusRepresentation.toSlotStatus(slotStatus.getInstanceId()));
            return slotStatus;
        }
        catch (Exception e) {
            log.error(e);
            return setErrorStatus(e.getMessage());
        }
    }
}
