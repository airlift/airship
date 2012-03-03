package com.proofpoint.galaxy.coordinator;

import com.google.common.base.Function;
import com.google.common.base.Preconditions;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.ning.http.client.AsyncHttpClient;
import com.ning.http.client.Response;
import com.proofpoint.discovery.client.ServiceDescriptor;
import com.proofpoint.discovery.client.ServiceDescriptorsRepresentation;
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
import java.util.concurrent.atomic.AtomicBoolean;

import static com.proofpoint.galaxy.shared.AgentLifecycleState.OFFLINE;
import static com.proofpoint.galaxy.shared.AgentLifecycleState.ONLINE;
import static com.proofpoint.galaxy.shared.AgentLifecycleState.PROVISIONING;
import static com.proofpoint.galaxy.shared.VersionsUtil.GALAXY_AGENT_VERSION_HEADER;

public class HttpRemoteAgent implements RemoteAgent
{
    private static final Logger log = Logger.get(HttpRemoteAgent.class);

    private final JsonCodec<InstallationRepresentation> installationCodec;
    private final JsonCodec<AgentStatusRepresentation> agentStatusCodec;
    private final JsonCodec<SlotStatusRepresentation> slotStatusCodec;
    private final JsonCodec<ServiceDescriptorsRepresentation> serviceDescriptorsCodec;

    private AgentStatus agentStatus;
    private String environment;
    private final AsyncHttpClient httpClient;

    private final AtomicBoolean serviceInventoryUp = new AtomicBoolean(true);

    public HttpRemoteAgent(AgentStatus agentStatus,
            String environment,
            AsyncHttpClient httpClient,
            JsonCodec<InstallationRepresentation> installationCodec,
            JsonCodec<AgentStatusRepresentation> agentStatusCodec,
            JsonCodec<SlotStatusRepresentation> slotStatusCodec,
            JsonCodec<ServiceDescriptorsRepresentation> serviceDescriptorsCodec)
    {
        Preconditions.checkNotNull(agentStatus, "agentStatus is null");
        Preconditions.checkNotNull(environment, "environment is null");
        Preconditions.checkNotNull(httpClient, "httpClient is null");

        this.agentStatus = agentStatus;
        this.environment = environment;
        this.httpClient = httpClient;
        this.installationCodec = installationCodec;
        this.agentStatusCodec = agentStatusCodec;
        this.slotStatusCodec = slotStatusCodec;
        this.serviceDescriptorsCodec = serviceDescriptorsCodec;
    }

    @Override
    public AgentStatus status()
    {
        return agentStatus;
    }

    @Override
    public void setInternalUri(URI internalUri)
    {
        agentStatus = agentStatus.changeInternalUri(internalUri);
    }

    @Override
    public List<? extends RemoteSlot> getSlots()
    {
        return ImmutableList.copyOf(Iterables.transform(agentStatus.getSlotStatuses(), new Function<SlotStatus, HttpRemoteSlot>()
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
        if (agentStatus.getState() == ONLINE) {
            Preconditions.checkNotNull(serviceInventory, "serviceInventory is null");
            URI internalUri = agentStatus.getInternalUri();
            try {
                httpClient.preparePut(internalUri + "/v1/serviceInventory")
                        .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON)
                        .setBody(serviceDescriptorsCodec.toJson(new ServiceDescriptorsRepresentation(environment, serviceInventory)))
                        .execute()
                        .get();

                if (serviceInventoryUp.compareAndSet(false, true)) {
                    log.info("Service inventory put succeeded for agent at %s", internalUri);
                }
            }
            catch (Exception e) {
                if (serviceInventoryUp.compareAndSet(true, false) && !log.isDebugEnabled()) {
                    log.error("Unable to post service inventory to agent at %s: %s", internalUri, e.getMessage());
                }
                log.debug(e, "Unable to post service inventory to agent at %s: %s", internalUri, e.getMessage());
            }
        }
    }

    @Override
    public void updateStatus()
    {
        URI internalUri = agentStatus.getInternalUri();
        if (internalUri != null) {
            try {
                String uri = internalUri.toString();
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
                    agentStatus = agentStatusRepresentation.toAgentStatus(agentStatus.getInstanceId(), agentStatus.getInstanceType());
                    return;
                }
            }
            catch (Exception ignored) {
            }
        }

        // error talking to agent -- mark agent offline
        if (agentStatus.getState() != PROVISIONING) {
            agentStatus = agentStatus.changeState(OFFLINE);
            agentStatus = agentStatus.changeAllSlotsState(SlotLifecycleState.UNKNOWN);
        }
    }

    public void setStatus(AgentStatus agentStatus)
    {
        Preconditions.checkNotNull(agentStatus, "agentStatus is null");
        this.agentStatus = agentStatus;
    }

    public void setSlotStatus(SlotStatus slotStatus)
    {
        agentStatus = agentStatus.changeSlotStatus(slotStatus);
    }

    @Override
    public SlotStatus install(Installation installation)
    {
        Preconditions.checkNotNull(installation, "installation is null");
        URI internalUri = agentStatus.getInternalUri();
        Preconditions.checkState(internalUri != null, "agent is down");
        try {
            Response response = httpClient.preparePost(internalUri + "/v1/agent/slot")
                    .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON)
                    .setHeader(GALAXY_AGENT_VERSION_HEADER, status().getVersion())
                    .setBody(installationCodec.toJson(InstallationRepresentation.from(installation)))
                    .execute()
                    .get();

            if (response.getStatusCode() != Status.CREATED.getStatusCode()) {
                throw new RuntimeException("Assignment Failed with " + response.getStatusCode() + " " + response.getStatusText());
            }
            String responseJson = response.getResponseBody();
            SlotStatusRepresentation slotStatusRepresentation = slotStatusCodec.fromJson(responseJson);

            SlotStatus slotStatus = slotStatusRepresentation.toSlotStatus();
            agentStatus = agentStatus.changeSlotStatus(slotStatus);

            return slotStatus;
        }
        catch (Exception e) {
            throw Throwables.propagate(e);
        }
    }
}

