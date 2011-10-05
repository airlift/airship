package com.proofpoint.galaxy.coordinator;

import com.google.common.base.Function;
import com.google.common.base.Preconditions;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.ning.http.client.AsyncHttpClient;
import com.ning.http.client.Response;
import com.proofpoint.galaxy.shared.AgentLifecycleState;
import com.proofpoint.galaxy.shared.AgentStatus;
import com.proofpoint.galaxy.shared.AgentStatusRepresentation;
import com.proofpoint.galaxy.shared.Installation;
import com.proofpoint.galaxy.shared.InstallationRepresentation;
import com.proofpoint.galaxy.shared.SlotLifecycleState;
import com.proofpoint.galaxy.shared.SlotStatus;
import com.proofpoint.galaxy.shared.SlotStatusRepresentation;
import com.proofpoint.json.JsonCodec;

import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response.Status;
import java.net.URI;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import static com.google.common.collect.Sets.newHashSet;
import static com.proofpoint.galaxy.shared.AgentLifecycleState.OFFLINE;

public class HttpRemoteAgent implements RemoteAgent
{
    private final JsonCodec<InstallationRepresentation> installationCodec;
    private final JsonCodec<AgentStatusRepresentation> agentStatusCodec;
    private final JsonCodec<SlotStatusRepresentation> slotStatusCodec;
    private final ConcurrentMap<UUID, HttpRemoteSlot> slots;

    private final String agentId;
    private final AsyncHttpClient httpClient;
    private AgentLifecycleState state;
    private URI uri;
    private String location;
    private String instanceType;

    public HttpRemoteAgent(String agentId,
            URI uri,
            AsyncHttpClient httpClient,
            JsonCodec<InstallationRepresentation> installationCodec,
            JsonCodec<AgentStatusRepresentation> agentStatusCodec,
            JsonCodec<SlotStatusRepresentation> slotStatusCodec)
    {
        this.agentId = agentId;
        this.uri = uri;
        this.httpClient = httpClient;
        this.installationCodec = installationCodec;
        this.agentStatusCodec = agentStatusCodec;
        this.slotStatusCodec = slotStatusCodec;

        slots = new ConcurrentHashMap<UUID, HttpRemoteSlot>();
        state = OFFLINE;
    }

    @Override
    public AgentStatus status()
    {
        return new AgentStatus(agentId, state, uri, location, instanceType, ImmutableList.copyOf(Iterables.transform(slots.values(), new Function<HttpRemoteSlot, SlotStatus>()
        {
            public SlotStatus apply(HttpRemoteSlot slot)
            {
                return slot.status();
            }
        })));
    }

    @Override
    public URI getUri()
    {
        return uri;
    }

    @Override
    public void setUri(URI uri)
    {
        this.uri = uri;
    }

    @Override
    public List<? extends RemoteSlot> getSlots()
    {
        return ImmutableList.copyOf(slots.values());
    }

    @Override
    public void updateStatus()
    {
        if (uri != null) {
            try {
                String uri = this.uri.toString();
                if (!uri.endsWith("/")) {
                    uri += "/";
                }
                uri += "v1/agent";
                Response response = httpClient.prepareGet(uri)
                        .execute()
                        .get();

                if (response.getStatusCode() == Status.OK.getStatusCode()) {
                    String responseJson = response.getResponseBody();
                    AgentStatusRepresentation agentStatusRepresentation = agentStatusCodec.fromJson(responseJson);
                    setStatus(agentStatusRepresentation.toAgentStatus());
                    return;
                }
            }
            catch (Exception ignored) {
            }
        }

        // error talking to agent -- mark agent offline
        state = OFFLINE;
        for (HttpRemoteSlot remoteSlot : slots.values()) {
            remoteSlot.updateStatus(new SlotStatus(remoteSlot.status(), SlotLifecycleState.UNKNOWN));
        }
    }

    @Override
    public void setStatus(AgentStatus status)
    {
        Set<UUID> updatedSlots = newHashSet();
        for (SlotStatus slotStatus : status.getSlotStatuses()) {
            HttpRemoteSlot remoteSlot = slots.get(slotStatus.getId());
            if (remoteSlot != null) {
                remoteSlot.updateStatus(slotStatus);
            }
            else {
                slots.put(slotStatus.getId(), new HttpRemoteSlot(slotStatus, httpClient));
            }
            updatedSlots.add(slotStatus.getId());
        }

        // remove all slots that were not updated
        slots.keySet().retainAll(updatedSlots);

        state = status.getState();
        uri = status.getUri();
        location = status.getLocation();
        instanceType = status.getInstanceType();
    }

    @Override
    public SlotStatus install(Installation installation)
    {
        Preconditions.checkNotNull(installation, "installation is null");
        Preconditions.checkState(uri != null, "agent is down");
        try {
            Response response = httpClient.preparePost(uri + "/v1/agent/slot")
                    .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON)
                    .setBody(installationCodec.toJson(InstallationRepresentation.from(installation)))
                    .execute()
                    .get();

            if (response.getStatusCode() != Status.CREATED.getStatusCode()) {
                throw new RuntimeException("Assignment Failed with " + response.getStatusCode() + " " + response.getStatusText());
            }
            String responseJson = response.getResponseBody();
            SlotStatusRepresentation slotStatusRepresentation = slotStatusCodec.fromJson(responseJson);

            SlotStatus slotStatus = slotStatusRepresentation.toSlotStatus();
            slots.put(slotStatus.getId(), new HttpRemoteSlot(slotStatus, httpClient));

            return slotStatus;
        }
        catch (Exception e) {
            throw Throwables.propagate(e);
        }
    }

    @Override
    public SlotStatus terminateSlot(UUID slotId)
    {
        Preconditions.checkNotNull(slotId, "slotId is null");

        HttpRemoteSlot slot = slots.get(slotId);
        SlotStatus status = slot.terminate();
        if (status.getState() == SlotLifecycleState.TERMINATED) {
            slots.remove(slotId);
        }
        return status;
    }
}

