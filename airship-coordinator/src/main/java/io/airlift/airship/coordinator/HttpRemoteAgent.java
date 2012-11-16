package io.airlift.airship.coordinator;

import com.google.common.base.Function;
import com.google.common.base.Preconditions;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import io.airlift.airship.shared.AgentStatus;
import io.airlift.airship.shared.AgentStatusRepresentation;
import io.airlift.airship.shared.Installation;
import io.airlift.airship.shared.InstallationRepresentation;
import io.airlift.airship.shared.SlotLifecycleState;
import io.airlift.airship.shared.SlotStatus;
import io.airlift.airship.shared.SlotStatusRepresentation;
import io.airlift.discovery.client.ServiceDescriptor;
import io.airlift.discovery.client.ServiceDescriptorsRepresentation;
import io.airlift.http.client.HttpClient;
import io.airlift.http.client.Request;
import io.airlift.json.JsonCodec;
import io.airlift.log.Logger;

import javax.ws.rs.core.Response.Status;
import java.net.URI;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.google.common.net.HttpHeaders.CONTENT_TYPE;
import static io.airlift.airship.shared.AgentLifecycleState.OFFLINE;
import static io.airlift.airship.shared.AgentLifecycleState.ONLINE;
import static io.airlift.airship.shared.AgentLifecycleState.PROVISIONING;
import static io.airlift.airship.shared.HttpUriBuilder.uriBuilderFrom;
import static io.airlift.airship.shared.VersionsUtil.AIRSHIP_AGENT_VERSION_HEADER;
import static io.airlift.http.client.JsonBodyGenerator.jsonBodyGenerator;
import static io.airlift.http.client.JsonResponseHandler.createJsonResponseHandler;
import static io.airlift.http.client.StatusResponseHandler.createStatusResponseHandler;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;

public class HttpRemoteAgent implements RemoteAgent
{
    private static final Logger log = Logger.get(HttpRemoteAgent.class);

    private final JsonCodec<InstallationRepresentation> installationCodec;
    private final JsonCodec<AgentStatusRepresentation> agentStatusCodec;
    private final JsonCodec<SlotStatusRepresentation> slotStatusCodec;
    private final JsonCodec<ServiceDescriptorsRepresentation> serviceDescriptorsCodec;

    private AgentStatus agentStatus;
    private String environment;
    private final HttpClient httpClient;

    private final AtomicBoolean serviceInventoryUp = new AtomicBoolean(true);

    public HttpRemoteAgent(AgentStatus agentStatus,
            String environment,
            HttpClient httpClient,
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
                Request request = Request.Builder.preparePut()
                        .setUri(uriBuilderFrom(internalUri).appendPath("/v1/serviceInventory").build())
                        .setHeader(CONTENT_TYPE, APPLICATION_JSON)
                        .setBodyGenerator(jsonBodyGenerator(serviceDescriptorsCodec, new ServiceDescriptorsRepresentation(environment, serviceInventory)))
                        .build();
                httpClient.execute(request, createStatusResponseHandler());

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
                Request request = Request.Builder.prepareGet()
                        .setUri(uriBuilderFrom(internalUri).replacePath("/v1/agent/").build())
                        .build();
                AgentStatusRepresentation agentStatusRepresentation = httpClient.execute(request, createJsonResponseHandler(agentStatusCodec));
                agentStatus = agentStatusRepresentation.toAgentStatus(agentStatus.getInstanceId(), agentStatus.getInstanceType());
                return;
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
            Request request = Request.Builder.preparePost()
                    .setUri(uriBuilderFrom(internalUri).replacePath("/v1/agent/slot/").build())
                    .setHeader(CONTENT_TYPE, APPLICATION_JSON)
                    .setHeader(AIRSHIP_AGENT_VERSION_HEADER, status().getVersion())
                    .setBodyGenerator(jsonBodyGenerator(installationCodec, InstallationRepresentation.from(installation)))
                    .build();
            SlotStatusRepresentation slotStatusRepresentation = httpClient.execute(request, createJsonResponseHandler(slotStatusCodec, Status.CREATED.getStatusCode()));

            SlotStatus slotStatus = slotStatusRepresentation.toSlotStatus(agentStatus.getInstanceId());
            agentStatus = agentStatus.changeSlotStatus(slotStatus);

            return slotStatus;
        }
        catch (Exception e) {
            throw Throwables.propagate(e);
        }
    }
}

