package com.proofpoint.galaxy.coordinator;

import com.google.common.base.Function;
import com.google.common.base.Preconditions;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.ning.http.client.AsyncHttpClient;
import com.ning.http.client.Response;
import com.proofpoint.discovery.client.ServiceDescriptor;
import com.proofpoint.discovery.client.ServiceDescriptorsRepresentation;
import com.proofpoint.galaxy.shared.AgentLifecycleState;
import com.proofpoint.galaxy.shared.AgentStatus;
import com.proofpoint.galaxy.shared.AgentStatusRepresentation;
import com.proofpoint.galaxy.shared.Installation;
import com.proofpoint.galaxy.shared.InstallationRepresentation;
import com.proofpoint.galaxy.shared.SlotLifecycleState;
import com.proofpoint.galaxy.shared.SlotStatus;
import com.proofpoint.galaxy.shared.SlotStatusRepresentation;
import com.proofpoint.json.JsonCodec;
import com.proofpoint.log.Logger;

import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response.Status;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.google.common.collect.Sets.newHashSet;
import static com.proofpoint.galaxy.shared.AgentLifecycleState.OFFLINE;
import static com.proofpoint.galaxy.shared.AgentLifecycleState.ONLINE;
import static com.proofpoint.galaxy.shared.AgentLifecycleState.PROVISIONING;

public class HttpRemoteAgent implements RemoteAgent
{
    private static final Logger log = Logger.get(HttpRemoteAgent.class);

    private final JsonCodec<InstallationRepresentation> installationCodec;
    private final JsonCodec<AgentStatusRepresentation> agentStatusCodec;
    private final JsonCodec<SlotStatusRepresentation> slotStatusCodec;
    private final JsonCodec<ServiceDescriptorsRepresentation> serviceDescriptorsCodec;

    private final ConcurrentMap<UUID, SlotStatus> slots;
    private final String environment;
    private final String agentId;
    private final AsyncHttpClient httpClient;
    private AgentLifecycleState state;
    private URI uri;
    private Map<String,Integer> resources = ImmutableMap.of();
    private String location;
    private String instanceType;
    private final AtomicBoolean serviceInventoryUp = new AtomicBoolean(true);

    public HttpRemoteAgent(String environment,
            String agentId,
            String instanceType,
            URI uri,
            AsyncHttpClient httpClient,
            JsonCodec<InstallationRepresentation> installationCodec,
            JsonCodec<AgentStatusRepresentation> agentStatusCodec,
            JsonCodec<SlotStatusRepresentation> slotStatusCodec,
            JsonCodec<ServiceDescriptorsRepresentation> serviceDescriptorsCodec)
    {
        this.environment = environment;
        this.agentId = agentId;
        this.instanceType = instanceType;
        this.uri = uri;
        this.httpClient = httpClient;
        this.installationCodec = installationCodec;
        this.agentStatusCodec = agentStatusCodec;
        this.slotStatusCodec = slotStatusCodec;
        this.serviceDescriptorsCodec = serviceDescriptorsCodec;

        slots = new ConcurrentHashMap<UUID, SlotStatus>();
        state = OFFLINE;
    }

    @Override
    public AgentStatus status()
    {
        return new AgentStatus(agentId, state, uri, location, instanceType, ImmutableList.copyOf(slots.values()), resources);
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
        return ImmutableList.copyOf(Iterables.transform(slots.values(), new Function<SlotStatus, HttpRemoteSlot>()
        {
            @Override
            public HttpRemoteSlot apply(SlotStatus slotStatus)
            {
                return new HttpRemoteSlot(slotStatus, httpClient, HttpRemoteAgent.this);
            }
        }));
    }

    @Override
    public void setServiceInventory(List<ServiceDescriptor> serviceInventory)
    {
        if (state == ONLINE) {
            Preconditions.checkNotNull(serviceInventory, "serviceInventory is null");
            try {
                httpClient.preparePut(uri + "/v1/serviceInventory")
                        .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON)
                        .setBody(serviceDescriptorsCodec.toJson(new ServiceDescriptorsRepresentation(environment, serviceInventory)))
                        .execute()
                        .get();

                if (serviceInventoryUp.compareAndSet(false, true)) {
                    log.info("Service inventory put succeeded for agent at %s", uri);
                }
            }
            catch (Exception e) {
                if (serviceInventoryUp.compareAndSet(true, false) && !log.isDebugEnabled()) {
                    log.error("Unable to post service inventory to agent at %s: %s", uri, e.getMessage());
                }
                log.debug(e, "Unable to post service inventory to agent at %s: %s", uri, e.getMessage());
            }
        }
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
        if (state != PROVISIONING) {
            state = OFFLINE;
            for (SlotStatus slotStatus : slots.values()) {
                slots.put(slotStatus.getId(), new SlotStatus(slotStatus, SlotLifecycleState.UNKNOWN, slotStatus.getAssignment()));
            }
        }
    }

    @Override
    public void setStatus(AgentStatus status)
    {
        Set<UUID> updatedSlots = newHashSet();
        for (SlotStatus slotStatus : status.getSlotStatuses()) {
            updatedSlots.add(slotStatus.getId());
            slots.put(slotStatus.getId(), slotStatus);
        }

        // remove all slots that were not updated
        slots.keySet().retainAll(updatedSlots);

        state = status.getState();
        uri = status.getUri();
        if (status.getResources() != null) {
            resources = ImmutableMap.copyOf(status.getResources());
        }
        else {
            resources = ImmutableMap.of();
        }
        location = status.getLocation();
    }

    public void setSlotStatus(SlotStatus slotStatus)
    {
        slots.put(slotStatus.getId(), slotStatus);
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
            slots.put(slotStatus.getId(), slotStatus);

            return slotStatus;
        }
        catch (Exception e) {
            throw Throwables.propagate(e);
        }
    }
}

