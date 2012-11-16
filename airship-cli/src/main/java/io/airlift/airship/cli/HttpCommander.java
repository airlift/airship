package io.airlift.airship.cli;

import com.google.common.base.Charsets;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import io.airlift.airship.coordinator.AgentProvisioningRepresentation;
import io.airlift.airship.coordinator.CoordinatorProvisioningRepresentation;
import io.airlift.airship.shared.AgentLifecycleState;
import io.airlift.airship.shared.AgentStatusRepresentation;
import io.airlift.airship.shared.Assignment;
import io.airlift.airship.shared.AssignmentRepresentation;
import io.airlift.airship.shared.CoordinatorLifecycleState;
import io.airlift.airship.shared.CoordinatorStatusRepresentation;
import io.airlift.airship.shared.SlotLifecycleState;
import io.airlift.airship.shared.SlotStatusRepresentation;
import io.airlift.airship.shared.UpgradeVersions;
import io.airlift.http.client.ApacheHttpClient;
import io.airlift.http.client.BodyGenerator;
import io.airlift.http.client.FullJsonResponseHandler.JsonResponse;
import io.airlift.http.client.HttpClient;
import io.airlift.http.client.HttpClientConfig;
import io.airlift.http.client.Request;
import io.airlift.json.JsonCodec;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.util.List;
import java.util.Map;

import static com.google.common.collect.Lists.newArrayList;
import static com.google.common.collect.Maps.newHashMap;
import static io.airlift.airship.cli.CommanderResponse.createCommanderResponse;
import static io.airlift.airship.cli.HttpCommander.TextBodyGenerator.textBodyGenerator;
import static io.airlift.airship.shared.HttpUriBuilder.uriBuilderFrom;
import static io.airlift.airship.shared.VersionsUtil.AIRSHIP_AGENTS_VERSION_HEADER;
import static io.airlift.airship.shared.VersionsUtil.AIRSHIP_SLOTS_VERSION_HEADER;
import static io.airlift.http.client.FullJsonResponseHandler.createFullJsonResponseHandler;
import static io.airlift.http.client.JsonBodyGenerator.jsonBodyGenerator;
import static io.airlift.http.client.JsonResponseHandler.createJsonResponseHandler;

public class HttpCommander implements Commander
{
    private static final JsonCodec<List<SlotStatusRepresentation>> SLOTS_CODEC = JsonCodec.listJsonCodec(SlotStatusRepresentation.class);
    private static final JsonCodec<AssignmentRepresentation> ASSIGNMENT_CODEC = JsonCodec.jsonCodec(AssignmentRepresentation.class);
    private static final JsonCodec<UpgradeVersions> UPGRADE_VERSIONS_CODEC = JsonCodec.jsonCodec(UpgradeVersions.class);

    private static final JsonCodec<List<CoordinatorStatusRepresentation>> COORDINATORS_CODEC = JsonCodec.listJsonCodec(CoordinatorStatusRepresentation.class);
    private static final JsonCodec<CoordinatorProvisioningRepresentation> COORDINATOR_PROVISIONING_CODEC = JsonCodec.jsonCodec(CoordinatorProvisioningRepresentation.class);

    private static final JsonCodec<AgentStatusRepresentation> AGENT_CODEC = JsonCodec.jsonCodec(AgentStatusRepresentation.class);
    private static final JsonCodec<List<AgentStatusRepresentation>> AGENTS_CODEC = JsonCodec.listJsonCodec(AgentStatusRepresentation.class);
    private static final JsonCodec<AgentProvisioningRepresentation> AGENT_PROVISIONING_CODEC = JsonCodec.jsonCodec(AgentProvisioningRepresentation.class);

    private final HttpClient client;
    private final URI coordinatorUri;
    private final boolean useInternalAddress;

    public HttpCommander(URI coordinatorUri, boolean useInternalAddress)
            throws IOException
    {
        Preconditions.checkNotNull(coordinatorUri, "coordinatorUri is null");
        this.coordinatorUri = coordinatorUri;
        this.client = new ApacheHttpClient(new HttpClientConfig());
        this.useInternalAddress = useInternalAddress;
    }

    @Override
    public CommanderResponse<List<SlotStatusRepresentation>> show(SlotFilter slotFilter)
    {
        URI uri = slotFilter.toUri(uriBuilderFrom(coordinatorUri).replacePath("/v1/slot"));
        Request request = Request.Builder.prepareGet()
                .setUri(uri)
                .build();

        JsonResponse<List<SlotStatusRepresentation>> response = client.execute(request, createFullJsonResponseHandler(SLOTS_CODEC));
        return createCommanderResponse(response.getHeader(AIRSHIP_SLOTS_VERSION_HEADER), response.getValue());
    }

    @Override
    public List<SlotStatusRepresentation> install(AgentFilter agentFilter, int count, Assignment assignment, String expectedVersion)
    {
        URI uri = agentFilter.toUri(uriBuilderFrom(coordinatorUri).replacePath("/v1/slot").addParameter("limit", String.valueOf(count)));
        Request.Builder requestBuilder = Request.Builder.preparePost()
                .setUri(uri)
                .setHeader("Content-Type", "application/json")
                .setBodyGenerator(jsonBodyGenerator(ASSIGNMENT_CODEC, AssignmentRepresentation.from(assignment)));
        if (expectedVersion != null) {
            requestBuilder.setHeader(AIRSHIP_SLOTS_VERSION_HEADER, expectedVersion);
        }

        List<SlotStatusRepresentation> slots = client.execute(requestBuilder.build(), createJsonResponseHandler(SLOTS_CODEC));
        return slots;
    }

    @Override
    public List<SlotStatusRepresentation> upgrade(SlotFilter slotFilter, UpgradeVersions upgradeVersions, String expectedVersion)
    {
        URI uri = slotFilter.toUri(uriBuilderFrom(coordinatorUri).replacePath("/v1/slot/assignment"));
        Request.Builder requestBuilder = Request.Builder.preparePost()
                .setUri(uri)
                .setHeader("Content-Type", "application/json")
                .setBodyGenerator(jsonBodyGenerator(UPGRADE_VERSIONS_CODEC, upgradeVersions));
        if (expectedVersion != null) {
            requestBuilder.setHeader(AIRSHIP_SLOTS_VERSION_HEADER, expectedVersion);
        }

        List<SlotStatusRepresentation> slots = client.execute(requestBuilder.build(), createJsonResponseHandler(SLOTS_CODEC));
        return slots;
    }

    @Override
    public List<SlotStatusRepresentation> setState(SlotFilter slotFilter, SlotLifecycleState state, String expectedVersion)
    {
        URI uri = slotFilter.toUri(uriBuilderFrom(coordinatorUri).replacePath("/v1/slot/lifecycle"));
        Request.Builder requestBuilder = Request.Builder.preparePut()
                .setUri(uri)
                .setBodyGenerator(textBodyGenerator(state.name()));
        if (expectedVersion != null) {
            requestBuilder.setHeader(AIRSHIP_SLOTS_VERSION_HEADER, expectedVersion);
        }

        List<SlotStatusRepresentation> slots = client.execute(requestBuilder.build(), createJsonResponseHandler(SLOTS_CODEC));
        return slots;
    }

    @Override
    public List<SlotStatusRepresentation> terminate(SlotFilter slotFilter, String expectedVersion)
    {
        URI uri = slotFilter.toUri(uriBuilderFrom(coordinatorUri).replacePath("/v1/slot"));
        Request.Builder requestBuilder = Request.Builder.prepareDelete().setUri(uri);
        if (expectedVersion != null) {
            requestBuilder.setHeader(AIRSHIP_SLOTS_VERSION_HEADER, expectedVersion);
        }

        List<SlotStatusRepresentation> slots = client.execute(requestBuilder.build(), createJsonResponseHandler(SLOTS_CODEC));
        return slots;
    }

    @Override
    public List<SlotStatusRepresentation> resetExpectedState(SlotFilter slotFilter, String expectedVersion)
    {
        URI uri = slotFilter.toUri(uriBuilderFrom(coordinatorUri).replacePath("/v1/slot/expected-state"));
        Request.Builder requestBuilder = Request.Builder.prepareDelete().setUri(uri);
        if (expectedVersion != null) {
            requestBuilder.setHeader(AIRSHIP_SLOTS_VERSION_HEADER, expectedVersion);
        }

        List<SlotStatusRepresentation> slots = client.execute(requestBuilder.build(), createJsonResponseHandler(SLOTS_CODEC));
        return slots;
    }

    @Override
    public boolean ssh(SlotFilter slotFilter, String command)
    {
        URI uri = slotFilter.toUri(uriBuilderFrom(coordinatorUri).replacePath("/v1/slot"));
        Request request = Request.Builder.prepareGet()
                .setUri(uri)
                .build();

        List<SlotStatusRepresentation> slots = client.execute(request, createJsonResponseHandler(SLOTS_CODEC));
        if (slots.isEmpty()) {
            return false;
        }
        SlotStatusRepresentation slot = slots.get(0);
        String host;
        if (useInternalAddress) {
            host = slot.getInternalHost();
        }
        else {
            host = slot.getExternalHost();
        }
        Exec.execRemote(host, slot.getInstallPath(), command);
        return true;
    }

    @Override
    public List<CoordinatorStatusRepresentation> showCoordinators(CoordinatorFilter coordinatorFilter)
    {
        URI uri = coordinatorFilter.toUri(uriBuilderFrom(coordinatorUri).replacePath("v1/admin/coordinator"));
        Request request = Request.Builder.prepareGet()
                .setUri(uri)
                .build();

        List<CoordinatorStatusRepresentation> coordinators = client.execute(request, createJsonResponseHandler(COORDINATORS_CODEC));
        return coordinators;
    }

    @Override
    public List<CoordinatorStatusRepresentation> provisionCoordinators(String coordinatorConfig,
            int coordinatorCount,
            String instanceType,
            String availabilityZone,
            String ami,
            String keyPair,
            String securityGroup,
            boolean waitForStartup)
    {
        URI uri = uriBuilderFrom(coordinatorUri).replacePath("v1/admin/coordinator").build();

        CoordinatorProvisioningRepresentation coordinatorProvisioning = new CoordinatorProvisioningRepresentation(
                coordinatorConfig,
                coordinatorCount,
                instanceType,
                availabilityZone,
                ami,
                keyPair,
                securityGroup);

        Request request = Request.Builder.preparePost()
                .setUri(uri)
                .setHeader("Content-Type", "application/json")
                .setBodyGenerator(jsonBodyGenerator(COORDINATOR_PROVISIONING_CODEC, coordinatorProvisioning))
                .build();

        List<CoordinatorStatusRepresentation> coordinators = client.execute(request, createJsonResponseHandler(COORDINATORS_CODEC));
        if (waitForStartup) {
            List<String> instanceIds = newArrayList();
            for (CoordinatorStatusRepresentation coordinator : coordinators) {
                instanceIds.add(coordinator.getInstanceId());
            }
            coordinators = waitForCoordinatorsToStart(instanceIds);
        }
        return coordinators;
    }

    private List<CoordinatorStatusRepresentation> waitForCoordinatorsToStart(List<String> instanceIds)
    {
        for (int loop = 0; true; loop++) {
            try {
                URI uri = uriBuilderFrom(coordinatorUri).replacePath("v1/admin/coordinator").build();
                Request request = Request.Builder.prepareGet()
                        .setUri(uri)
                        .build();
                List<CoordinatorStatusRepresentation> coordinators = client.execute(request, createJsonResponseHandler(COORDINATORS_CODEC));

                Map<String, CoordinatorStatusRepresentation> runningCoordinators = newHashMap();
                for (CoordinatorStatusRepresentation coordinator : coordinators) {
                    if (coordinator.getState() == CoordinatorLifecycleState.ONLINE) {
                        runningCoordinators.put(coordinator.getInstanceId(), coordinator);
                    }
                }
                if (runningCoordinators.keySet().containsAll(instanceIds)) {
                    WaitUtils.clearWaitMessage();
                    runningCoordinators.keySet().retainAll(instanceIds);
                    return ImmutableList.copyOf(runningCoordinators.values());
                }
            }
            catch (Exception ignored) {
            }

            WaitUtils.wait(loop);
        }
    }

    @Override
    public boolean sshCoordinator(CoordinatorFilter coordinatorFilter, String command)
    {
        URI uri = coordinatorFilter.toUri(uriBuilderFrom(coordinatorUri).replacePath("v1/admin/coordinator"));
        Request request = Request.Builder.prepareGet()
                .setUri(uri)
                .build();

        List<CoordinatorStatusRepresentation> coordinators = client.execute(request, createJsonResponseHandler(COORDINATORS_CODEC));
        if (coordinators.isEmpty()) {
            return false;
        }
        Exec.execRemote(coordinators.get(0).getExternalHost(), command);
        return true;
    }

    @Override
    public CommanderResponse<List<AgentStatusRepresentation>> showAgents(AgentFilter agentFilter)
    {
        URI uri = agentFilter.toUri(uriBuilderFrom(coordinatorUri).replacePath("v1/admin/agent"));
        Request request = Request.Builder.prepareGet()
                .setUri(uri)
                .build();

        JsonResponse<List<AgentStatusRepresentation>> response = client.execute(request, createFullJsonResponseHandler(AGENTS_CODEC));
        if (response.getStatusCode() != 200) {
            throw new RuntimeException(response.getStatusMessage());
        }
        return CommanderResponse.createCommanderResponse(response.getHeader(AIRSHIP_AGENTS_VERSION_HEADER), response.getValue());
    }

    @Override
    public List<AgentStatusRepresentation> provisionAgents(String agentConfig,
            int agentCount,
            String instanceType,
            String availabilityZone,
            String ami,
            String keyPair,
            String securityGroup,
            boolean waitForStartup)
    {
        URI uri = uriBuilderFrom(coordinatorUri).replacePath("v1/admin/agent").build();

        AgentProvisioningRepresentation agentProvisioning = new AgentProvisioningRepresentation(
                agentConfig,
                agentCount,
                instanceType,
                availabilityZone,
                ami,
                keyPair,
                securityGroup);

        Request request = Request.Builder.preparePost()
                .setUri(uri)
                .setHeader("Content-Type", "application/json")
                .setBodyGenerator(jsonBodyGenerator(AGENT_PROVISIONING_CODEC, agentProvisioning))
                .build();

        List<AgentStatusRepresentation> agents = client.execute(request, createJsonResponseHandler(AGENTS_CODEC));
        if (waitForStartup) {
            List<String> instanceIds = newArrayList();
            for (AgentStatusRepresentation agent : agents) {
                instanceIds.add(agent.getInstanceId());
            }
            agents = waitForAgentsToStart(instanceIds);
        }
        return agents;
    }

    private List<AgentStatusRepresentation> waitForAgentsToStart(List<String> instanceIds)
    {
        for (int loop = 0; true; loop++) {
            try {
                URI uri = uriBuilderFrom(coordinatorUri).replacePath("v1/admin/agent").build();
                Request request = Request.Builder.prepareGet()
                        .setUri(uri)
                        .build();
                List<AgentStatusRepresentation> agents = client.execute(request, createJsonResponseHandler(AGENTS_CODEC));

                Map<String, AgentStatusRepresentation> runningAgents = newHashMap();
                for (AgentStatusRepresentation agent : agents) {
                    if (agent.getState() == AgentLifecycleState.ONLINE) {
                        runningAgents.put(agent.getInstanceId(), agent);
                    }
                }
                if (runningAgents.keySet().containsAll(instanceIds)) {
                    WaitUtils.clearWaitMessage();
                    runningAgents.keySet().retainAll(instanceIds);
                    return ImmutableList.copyOf(runningAgents.values());
                }
            }
            catch (Exception ignored) {
            }

            WaitUtils.wait(loop);
        }
    }

    @Override
    public AgentStatusRepresentation terminateAgent(String agentId)
    {
        URI uri = uriBuilderFrom(coordinatorUri).replacePath("v1/admin/agent").build();

        Request request = Request.Builder.prepareDelete()
                .setUri(uri)
                .setBodyGenerator(textBodyGenerator(agentId))
                .build();

        AgentStatusRepresentation agents = client.execute(request, createJsonResponseHandler(AGENT_CODEC));
        return agents;
    }

    @Override
    public boolean sshAgent(AgentFilter agentFilter, String command)
    {
        URI uri = agentFilter.toUri(uriBuilderFrom(coordinatorUri).replacePath("v1/admin/agent"));
        Request request = Request.Builder.prepareGet()
                .setUri(uri)
                .build();

        List<AgentStatusRepresentation> agents = client.execute(request, createJsonResponseHandler(AGENTS_CODEC));
        if (agents.isEmpty()) {
            return false;
        }
        Exec.execRemote(agents.get(0).getExternalHost(), command);
        return true;
    }

    public static class TextBodyGenerator implements BodyGenerator
    {
        public static TextBodyGenerator textBodyGenerator(String instance)
        {
            return new TextBodyGenerator(instance);
        }

        private byte[] text;

        private TextBodyGenerator(String text)
        {
            this.text = text.getBytes(Charsets.UTF_8);
        }

        @Override
        public void write(OutputStream out)
                throws Exception
        {
            out.write(text);
        }
    }
}
