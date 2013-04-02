package io.airlift.airship.coordinator;

import com.google.common.base.Function;
import com.google.common.base.Preconditions;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.util.concurrent.CheckedFuture;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import io.airlift.airship.shared.AgentStatus;
import io.airlift.airship.shared.AgentStatusRepresentation;
import io.airlift.airship.shared.Installation;
import io.airlift.airship.shared.InstallationRepresentation;
import io.airlift.airship.shared.SlotLifecycleState;
import io.airlift.airship.shared.SlotStatus;
import io.airlift.airship.shared.SlotStatusRepresentation;
import io.airlift.discovery.client.ServiceDescriptor;
import io.airlift.discovery.client.ServiceDescriptorsRepresentation;
import io.airlift.http.client.AsyncHttpClient;
import io.airlift.http.client.Request;
import io.airlift.http.client.StatusResponseHandler;
import io.airlift.json.JsonCodec;
import io.airlift.log.Logger;

import javax.ws.rs.core.Response.Status;
import java.net.URI;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import static com.google.common.net.HttpHeaders.CONTENT_TYPE;
import static io.airlift.airship.shared.AgentLifecycleState.OFFLINE;
import static io.airlift.airship.shared.AgentLifecycleState.ONLINE;
import static io.airlift.airship.shared.AgentLifecycleState.PROVISIONING;
import static io.airlift.airship.shared.HttpUriBuilder.uriBuilderFrom;
import static io.airlift.airship.shared.VersionsUtil.AIRSHIP_AGENT_VERSION_HEADER;
import static io.airlift.http.client.JsonBodyGenerator.jsonBodyGenerator;
import static io.airlift.http.client.JsonResponseHandler.createJsonResponseHandler;
import static io.airlift.http.client.StatusResponseHandler.StatusResponse;
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
    private final String environment;
    private final AsyncHttpClient httpClient;

    private final AtomicLong failureCount = new AtomicLong();

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
    public synchronized AgentStatus status()
    {
        return agentStatus;
    }

    @Override
    public synchronized void setInternalUri(URI internalUri)
    {
        agentStatus = agentStatus.changeInternalUri(internalUri);
    }

    @Override
    public synchronized List<? extends RemoteSlot> getSlots()
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
        AgentStatus agentStatus = status();
        if (agentStatus.getState() == ONLINE) {
            Preconditions.checkNotNull(serviceInventory, "serviceInventory is null");
            final URI internalUri = agentStatus.getInternalUri();
            Request request = Request.Builder.preparePut()
                    .setUri(uriBuilderFrom(internalUri).replacePath("/v1/serviceInventory").build())
                    .setHeader(CONTENT_TYPE, APPLICATION_JSON)
                    .setBodyGenerator(jsonBodyGenerator(serviceDescriptorsCodec, new ServiceDescriptorsRepresentation(environment, serviceInventory)))
                    .build();

            Futures.addCallback(httpClient.executeAsync(request, createStatusResponseHandler()), new FutureCallback<StatusResponseHandler.StatusResponse>()
            {
                @Override
                public void onSuccess(StatusResponse result)
                {
                    if (serviceInventoryUp.compareAndSet(false, true)) {
                        log.info("Service inventory put succeeded for agent at %s", internalUri);
                    }
                }

                @Override
                public void onFailure(Throwable t)
                {
                    if (serviceInventoryUp.compareAndSet(true, false) && !log.isDebugEnabled()) {
                        log.error("Unable to post service inventory to agent at %s: %s", internalUri, t.getMessage());
                    }
                    log.debug(t, "Unable to post service inventory to agent at %s: %s", internalUri, t.getMessage());
                }
            });
        }
    }

    @Override
    public ListenableFuture<?> updateStatus()
    {
        final AgentStatus agentStatus = status();
        URI internalUri = agentStatus.getInternalUri();
        if (internalUri != null) {
            Request request = Request.Builder.prepareGet()
                    .setUri(uriBuilderFrom(internalUri).replacePath("/v1/agent/").build())
                    .build();

            CheckedFuture<AgentStatusRepresentation, RuntimeException> future = httpClient.executeAsync(request, createJsonResponseHandler(agentStatusCodec));
            Futures.addCallback(future, new FutureCallback<AgentStatusRepresentation>()
            {
                @Override
                public void onSuccess(AgentStatusRepresentation result)
                {
                    // todo deal with out of order responses
                    setStatus(result.toAgentStatus(agentStatus.getInstanceId(), agentStatus.getInstanceType()));
                    failureCount.set(0);
                }

                @Override
                public void onFailure(Throwable t)
                {
                    // error talking to agent -- mark agent offline
                    if (agentStatus.getState() != PROVISIONING && failureCount.incrementAndGet() > 5) {
                        setStatus(agentStatus.changeState(OFFLINE).changeAllSlotsState(SlotLifecycleState.UNKNOWN));
                    }
                }
            });
            return future;
        }
        return Futures.immediateFuture(null);
    }

    public synchronized void setStatus(AgentStatus agentStatus)
    {
        Preconditions.checkNotNull(agentStatus, "agentStatus is null");
        this.agentStatus = agentStatus;
    }

    public synchronized void setSlotStatus(SlotStatus slotStatus)
    {
        agentStatus = agentStatus.changeSlotStatus(slotStatus);
    }

    @Override
    public SlotStatus install(Installation installation)
    {
        Preconditions.checkNotNull(installation, "installation is null");
        AgentStatus agentStatus = status();
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
            setStatus(agentStatus.changeSlotStatus(slotStatus));

            return slotStatus;
        }
        catch (Exception e) {
            throw Throwables.propagate(e);
        }
    }
}

