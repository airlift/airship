package com.proofpoint.galaxy.cli;

import com.google.common.base.Charsets;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.proofpoint.galaxy.coordinator.AgentProvisioningRepresentation;
import com.proofpoint.galaxy.coordinator.CoordinatorProvisioningRepresentation;
import com.proofpoint.galaxy.shared.AgentLifecycleState;
import com.proofpoint.galaxy.shared.AgentStatusRepresentation;
import com.proofpoint.galaxy.shared.Assignment;
import com.proofpoint.galaxy.shared.AssignmentRepresentation;
import com.proofpoint.galaxy.shared.CoordinatorLifecycleState;
import com.proofpoint.galaxy.shared.CoordinatorStatusRepresentation;
import com.proofpoint.galaxy.shared.JsonResponseHandler;
import com.proofpoint.galaxy.shared.SlotLifecycleState;
import com.proofpoint.galaxy.shared.SlotStatusRepresentation;
import com.proofpoint.galaxy.shared.UpgradeVersions;
import com.proofpoint.http.client.BodyGenerator;
import com.proofpoint.http.client.HttpClient;
import com.proofpoint.http.client.Request;
import com.proofpoint.http.client.RequestBuilder;
import com.proofpoint.json.JsonCodec;

import java.io.OutputStream;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;

import static com.google.common.collect.Lists.newArrayList;
import static com.google.common.collect.Maps.newHashMap;
import static com.proofpoint.galaxy.shared.HttpUriBuilder.uriBuilderFrom;
import static com.proofpoint.galaxy.cli.HttpCommander.TextBodyGenerator.textBodyGenerator;
import static com.proofpoint.http.client.JsonBodyGenerator.jsonBodyGenerator;

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

    public HttpCommander(URI coordinatorUri)
    {
        Preconditions.checkNotNull(coordinatorUri, "coordinatorUri is null");
        this.coordinatorUri = coordinatorUri;
        this.client = new HttpClient(Executors.newCachedThreadPool(new ThreadFactoryBuilder().setNameFormat("http-%s").setDaemon(true).build()));
    }

    @Override
    public List<Record> show(SlotFilter slotFilter)
    {
        URI uri = slotFilter.toUri(uriBuilderFrom(coordinatorUri).replacePath("/v1/slot"));
        Request request = RequestBuilder.prepareGet()
                .setUri(uri)
                .build();

        List<SlotStatusRepresentation> slots = client.execute(request, JsonResponseHandler.create(SLOTS_CODEC)).checkedGet();
        ImmutableList<Record> records = SlotRecord.toSlotRecords(slots);
        return records;
    }

    @Override
    public List<Record> install(AgentFilter agentFilter, int count, Assignment assignment)
    {
        URI uri = agentFilter.toUri(uriBuilderFrom(coordinatorUri).replacePath("/v1/slot"));
        Request request = RequestBuilder.preparePost()
                .setUri(uri)
                .setHeader("Content-Type", "application/json")
                .setBodyGenerator(jsonBodyGenerator(ASSIGNMENT_CODEC, AssignmentRepresentation.from(assignment)))
                .build();

        List<SlotStatusRepresentation> slots = client.execute(request, JsonResponseHandler.create(SLOTS_CODEC)).checkedGet();
        ImmutableList<Record> records = SlotRecord.toSlotRecords(slots);
        return records;
    }

    @Override
    public List<Record> upgrade(SlotFilter slotFilter, UpgradeVersions upgradeVersions)
    {
        URI uri = slotFilter.toUri(uriBuilderFrom(coordinatorUri).replacePath("/v1/slot/assignment"));
        Request request = RequestBuilder.preparePost()
                .setUri(uri)
                .setHeader("Content-Type", "application/json")
                .setBodyGenerator(jsonBodyGenerator(UPGRADE_VERSIONS_CODEC, upgradeVersions))
                .build();

        List<SlotStatusRepresentation> slots = client.execute(request, JsonResponseHandler.create(SLOTS_CODEC)).checkedGet();
        ImmutableList<Record> records = SlotRecord.toSlotRecords(slots);
        return records;
    }

    @Override
    public List<Record> setState(SlotFilter slotFilter, SlotLifecycleState state)
    {
        URI uri = slotFilter.toUri(uriBuilderFrom(coordinatorUri).replacePath("/v1/slot/lifecycle"));
        Request request = RequestBuilder.preparePut()
                .setUri(uri)
                .setBodyGenerator(textBodyGenerator(state.name()))
                .build();

        List<SlotStatusRepresentation> slots = client.execute(request, JsonResponseHandler.create(SLOTS_CODEC)).checkedGet();
        ImmutableList<Record> records = SlotRecord.toSlotRecords(slots);
        return records;
    }

    @Override
    public List<Record> terminate(SlotFilter slotFilter)
    {
        URI uri = slotFilter.toUri(uriBuilderFrom(coordinatorUri).replacePath("/v1/slot"));
        Request request = RequestBuilder.prepareDelete()
                .setUri(uri)
                .build();

        List<SlotStatusRepresentation> slots = client.execute(request, JsonResponseHandler.create(SLOTS_CODEC)).checkedGet();
        ImmutableList<Record> records = SlotRecord.toSlotRecords(slots);
        return records;
    }

    @Override
    public List<Record> resetExpectedState(SlotFilter slotFilter)
    {
        URI uri = slotFilter.toUri(uriBuilderFrom(coordinatorUri).replacePath("/v1/slot/expected-state"));
        Request request = RequestBuilder.prepareDelete()
                .setUri(uri)
                .build();

        List<SlotStatusRepresentation> slots = client.execute(request, JsonResponseHandler.create(SLOTS_CODEC)).checkedGet();
        ImmutableList<Record> records = SlotRecord.toSlotRecords(slots);
        return records;
    }

    @Override
    public boolean ssh(SlotFilter slotFilter, String command)
    {
        URI uri = slotFilter.toUri(uriBuilderFrom(coordinatorUri).replacePath("/v1/slot"));
        Request request = RequestBuilder.prepareGet()
                .setUri(uri)
                .build();

        List<SlotStatusRepresentation> slots = client.execute(request, JsonResponseHandler.create(SLOTS_CODEC)).checkedGet();
        if (slots.isEmpty()) {
            return false;
        }
        Exec.execRemote(slots.get(0), command);
        return true;
    }

    @Override
    public List<Record> showCoordinators(CoordinatorFilter coordinatorFilter)
    {
        URI uri = coordinatorFilter.toUri(uriBuilderFrom(coordinatorUri).replacePath("v1/admin/coordinator"));
        Request request = RequestBuilder.prepareGet()
                .setUri(uri)
                .build();

        List<CoordinatorStatusRepresentation> coordinators = client.execute(request, JsonResponseHandler.create(COORDINATORS_CODEC)).checkedGet();
        ImmutableList<Record> records = CoordinatorRecord.toCoordinatorRecords(coordinators);
        return records;
    }

    @Override
    public List<Record> provisionCoordinators(String coordinatorConfig,
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

        Request request = RequestBuilder.preparePost()
                .setUri(uri)
                .setHeader("Content-Type", "application/json")
                .setBodyGenerator(jsonBodyGenerator(COORDINATOR_PROVISIONING_CODEC, coordinatorProvisioning))
                .build();

        List<CoordinatorStatusRepresentation> coordinators = client.execute(request, JsonResponseHandler.create(COORDINATORS_CODEC)).checkedGet();
        if (waitForStartup) {
            List<String> instanceIds = newArrayList();
            for (CoordinatorStatusRepresentation coordinator : coordinators) {
                instanceIds.add(coordinator.getCoordinatorId());
            }
            coordinators = waitForCoordinatorsToStart(instanceIds);
        }
        ImmutableList<Record> records = CoordinatorRecord.toCoordinatorRecords(coordinators);
        return records;
    }

    private List<CoordinatorStatusRepresentation> waitForCoordinatorsToStart(List<String> instanceIds)
    {
        for (int loop = 0; true; loop++) {
            try {
                URI uri = uriBuilderFrom(coordinatorUri).replacePath("v1/admin/coordinator").build();
                Request request = RequestBuilder.prepareGet()
                        .setUri(uri)
                        .build();
                List<CoordinatorStatusRepresentation> coordinators = client.execute(request, JsonResponseHandler.create(COORDINATORS_CODEC)).checkedGet();

                Map<String, CoordinatorStatusRepresentation> runningCoordinators = newHashMap();
                for (CoordinatorStatusRepresentation coordinator : coordinators) {
                    if (coordinator.getState() == CoordinatorLifecycleState.ONLINE) {
                        runningCoordinators.put(coordinator.getCoordinatorId(), coordinator);
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
        Request request = RequestBuilder.prepareGet()
                .setUri(uri)
                .build();

        List<CoordinatorStatusRepresentation> coordinators = client.execute(request, JsonResponseHandler.create(COORDINATORS_CODEC)).checkedGet();
        if (coordinators.isEmpty()) {
            return false;
        }
        Exec.execRemote(coordinators.get(0).getExternalHost(), command);
        return true;
    }

    @Override
    public List<Record> showAgents(AgentFilter agentFilter)
            throws Exception
    {
        URI uri = agentFilter.toUri(uriBuilderFrom(coordinatorUri).replacePath("v1/admin/agent"));
        Request request = RequestBuilder.prepareGet()
                .setUri(uri)
                .build();

        List<AgentStatusRepresentation> agents = client.execute(request, JsonResponseHandler.create(AGENTS_CODEC)).checkedGet();
        ImmutableList<Record> records = AgentRecord.toAgentRecords(agents);
        return records;
    }

    @Override
    public List<Record> provisionAgents(String agentConfig,
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

        Request request = RequestBuilder.preparePost()
                .setUri(uri)
                .setHeader("Content-Type", "application/json")
                .setBodyGenerator(jsonBodyGenerator(AGENT_PROVISIONING_CODEC, agentProvisioning))
                .build();

        List<AgentStatusRepresentation> agents = client.execute(request, JsonResponseHandler.create(AGENTS_CODEC)).checkedGet();
        if (waitForStartup) {
            List<String> instanceIds = newArrayList();
            for (AgentStatusRepresentation agent : agents) {
                instanceIds.add(agent.getAgentId());
            }
            agents = waitForAgentsToStart(instanceIds);
        }
        ImmutableList<Record> records = AgentRecord.toAgentRecords(agents);
        return records;
    }

    private List<AgentStatusRepresentation> waitForAgentsToStart(List<String> instanceIds)
    {
        for (int loop = 0; true; loop++) {
            try {
                URI uri = uriBuilderFrom(coordinatorUri).replacePath("v1/admin/agent").build();
                Request request = RequestBuilder.prepareGet()
                        .setUri(uri)
                        .build();
                List<AgentStatusRepresentation> agents = client.execute(request, JsonResponseHandler.create(AGENTS_CODEC)).checkedGet();

                Map<String, AgentStatusRepresentation> runningAgents = newHashMap();
                for (AgentStatusRepresentation agent : agents) {
                    if (agent.getState() == AgentLifecycleState.ONLINE) {
                        runningAgents.put(agent.getAgentId(), agent);
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
    public Record terminateAgent(String agentId)
    {
        URI uri = uriBuilderFrom(coordinatorUri).replacePath("v1/admin/agent").build();

        Request request = RequestBuilder.prepareDelete()
                .setUri(uri)
                .setBodyGenerator(textBodyGenerator(agentId))
                .build();

        AgentStatusRepresentation agents = client.execute(request, JsonResponseHandler.create(AGENT_CODEC)).checkedGet();
        AgentRecord record = new AgentRecord(agents);
        return record;
    }

    @Override
    public boolean sshAgent(AgentFilter agentFilter, String command)
    {
        URI uri = agentFilter.toUri(uriBuilderFrom(coordinatorUri).replacePath("v1/admin/agent"));
        Request request = RequestBuilder.prepareGet()
                .setUri(uri)
                .build();

        List<AgentStatusRepresentation> agents = client.execute(request, JsonResponseHandler.create(AGENTS_CODEC)).checkedGet();
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
