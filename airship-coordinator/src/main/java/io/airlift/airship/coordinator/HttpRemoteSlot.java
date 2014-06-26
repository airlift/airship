package io.airlift.airship.coordinator;

import com.google.common.base.Preconditions;
import io.airlift.airship.shared.Installation;
import io.airlift.airship.shared.InstallationRepresentation;
import io.airlift.airship.shared.SlotStatus;
import io.airlift.airship.shared.SlotStatusRepresentation;
import io.airlift.http.client.HttpClient;
import io.airlift.http.client.Request;
import io.airlift.json.JsonCodec;
import io.airlift.log.Logger;

import javax.ws.rs.core.Response.Status;

import java.util.UUID;

import static com.google.common.base.Charsets.UTF_8;
import static com.google.common.net.HttpHeaders.CONTENT_TYPE;
import static io.airlift.airship.shared.HttpUriBuilder.uriBuilderFrom;
import static io.airlift.airship.shared.SlotLifecycleState.UNKNOWN;
import static io.airlift.airship.shared.VersionsUtil.AIRSHIP_AGENT_VERSION_HEADER;
import static io.airlift.airship.shared.VersionsUtil.AIRSHIP_SLOT_VERSION_HEADER;
import static io.airlift.http.client.JsonBodyGenerator.jsonBodyGenerator;
import static io.airlift.http.client.JsonResponseHandler.createJsonResponseHandler;
import static io.airlift.http.client.StaticBodyGenerator.createStaticBodyGenerator;
import static io.airlift.json.JsonCodec.jsonCodec;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;

public class HttpRemoteSlot
        implements RemoteSlot
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
            Request request = Request.Builder.preparePut()
                    .setUri(uriBuilderFrom(slotStatus.getSelf()).appendPath("assignment").build())
                    .setHeader(CONTENT_TYPE, APPLICATION_JSON)
                    .setHeader(AIRSHIP_AGENT_VERSION_HEADER, agent.status().getVersion())
                    .setHeader(AIRSHIP_SLOT_VERSION_HEADER, slotStatus.getVersion())
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
            Request request = Request.Builder.prepareDelete()
                    .setUri(slotStatus.getSelf())
                    .setHeader(AIRSHIP_AGENT_VERSION_HEADER, agent.status().getVersion())
                    .setHeader(AIRSHIP_SLOT_VERSION_HEADER, slotStatus.getVersion())
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
            Request request = Request.Builder.preparePut()
                    .setUri(uriBuilderFrom(slotStatus.getSelf()).appendPath("lifecycle").build())
                    .setHeader(AIRSHIP_AGENT_VERSION_HEADER, agent.status().getVersion())
                    .setHeader(AIRSHIP_SLOT_VERSION_HEADER, slotStatus.getVersion())
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
            Request request = Request.Builder.preparePut()
                    .setUri(uriBuilderFrom(slotStatus.getSelf()).appendPath("lifecycle").build())
                    .setHeader(AIRSHIP_AGENT_VERSION_HEADER, agent.status().getVersion())
                    .setHeader(AIRSHIP_SLOT_VERSION_HEADER, slotStatus.getVersion())
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
            Request request = Request.Builder.preparePut()
                    .setUri(uriBuilderFrom(slotStatus.getSelf()).appendPath("lifecycle").build())
                    .setHeader(AIRSHIP_AGENT_VERSION_HEADER, agent.status().getVersion())
                    .setHeader(AIRSHIP_SLOT_VERSION_HEADER, slotStatus.getVersion())
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

    @Override
    public SlotStatus kill()
    {
        try {
            Request request = Request.Builder.preparePut()
                    .setUri(uriBuilderFrom(slotStatus.getSelf()).appendPath("lifecycle").build())
                    .setBodyGenerator(createStaticBodyGenerator("killing", UTF_8))
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
