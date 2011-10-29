package com.proofpoint.galaxy.standalone;

import com.google.common.base.Charsets;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.proofpoint.galaxy.coordinator.AgentProvisioningRepresentation;
import com.proofpoint.galaxy.shared.AgentStatusRepresentation;
import com.proofpoint.galaxy.shared.Assignment;
import com.proofpoint.galaxy.shared.AssignmentRepresentation;
import com.proofpoint.galaxy.shared.JsonResponseHandler;
import com.proofpoint.galaxy.shared.SlotLifecycleState;
import com.proofpoint.galaxy.shared.SlotStatusRepresentation;
import com.proofpoint.galaxy.shared.UpgradeVersions;
import com.proofpoint.http.client.BodyGenerator;
import com.proofpoint.http.client.HttpClient;
import com.proofpoint.http.client.Request;
import com.proofpoint.http.client.RequestBuilder;
import com.proofpoint.json.JsonCodec;

import javax.ws.rs.core.UriBuilder;
import java.io.OutputStream;
import java.net.URI;
import java.util.List;
import java.util.concurrent.Executors;

import static com.proofpoint.galaxy.standalone.HttpCommander.TextBodyGenerator.textBodyGenerator;
import static com.proofpoint.http.client.JsonBodyGenerator.jsonBodyGenerator;

public class HttpCommander implements Commander
{
    private static final JsonCodec<List<SlotStatusRepresentation>> SLOTS_CODEC = JsonCodec.listJsonCodec(SlotStatusRepresentation.class);
    private static final JsonCodec<AssignmentRepresentation> ASSIGNMENT_CODEC = JsonCodec.jsonCodec(AssignmentRepresentation.class);
    private static final JsonCodec<UpgradeVersions> UPGRADE_VERSIONS_CODEC = JsonCodec.jsonCodec(UpgradeVersions.class);

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
        URI uri = slotFilter.toUri(UriBuilder.fromUri(coordinatorUri).path("/v1/slot"));
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
        URI uri = agentFilter.toUri(UriBuilder.fromUri(coordinatorUri).path("/v1/slot"));
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
        URI uri = slotFilter.toUri(UriBuilder.fromUri(coordinatorUri).path("/v1/slot/assignment"));
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
        URI uri = slotFilter.toUri(UriBuilder.fromUri(coordinatorUri).path("/v1/slot/lifecycle"));
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
        URI uri = slotFilter.toUri(UriBuilder.fromUri(coordinatorUri).path("/v1/slot"));
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
        URI uri = slotFilter.toUri(UriBuilder.fromUri(coordinatorUri).path("/v1/slot/expected-state"));
        Request request = RequestBuilder.prepareDelete()
                .setUri(uri)
                .build();

        List<SlotStatusRepresentation> slots = client.execute(request, JsonResponseHandler.create(SLOTS_CODEC)).checkedGet();
        ImmutableList<Record> records = SlotRecord.toSlotRecords(slots);
        return records;
    }

    @Override
    public void ssh(SlotFilter slotFilter, List<String> args)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<Record> showAgents(AgentFilter agentFilter)
            throws Exception
    {
        URI uri = agentFilter.toUri(UriBuilder.fromUri(coordinatorUri).path("v1/admin/agent"));
        Request request = RequestBuilder.prepareGet()
                .setUri(uri)
                .build();

        List<AgentStatusRepresentation> agents = client.execute(request, JsonResponseHandler.create(AGENTS_CODEC)).checkedGet();
        ImmutableList<Record> records = AgentRecord.toAgentRecords(agents);
        return records;
    }

    @Override
    public List<Record> addAgents(int count, String instanceType, String availabilityZone)
            throws Exception
    {
        URI uri = UriBuilder.fromUri(coordinatorUri).path("v1/admin/agent").queryParam("count", count).build();

        AgentProvisioningRepresentation agentProvisioning = new AgentProvisioningRepresentation(instanceType, availabilityZone);

        Request request = RequestBuilder.preparePost()
                .setUri(uri)
                .setHeader("Content-Type", "application/json")
                .setBodyGenerator(jsonBodyGenerator(AGENT_PROVISIONING_CODEC, agentProvisioning))
                .build();

        List<AgentStatusRepresentation> agents = client.execute(request, JsonResponseHandler.create(AGENTS_CODEC)).checkedGet();
        ImmutableList<Record> records = AgentRecord.toAgentRecords(agents);
        return records;
    }

    @Override
    public Record terminateAgent(String agentId)
    {
        URI uri = UriBuilder.fromUri(coordinatorUri).path("v1/admin/agent").build();

        Request request = RequestBuilder.prepareDelete()
                .setUri(uri)
                .setBodyGenerator(textBodyGenerator(agentId))
                .build();

        AgentStatusRepresentation agents = client.execute(request, JsonResponseHandler.create(AGENT_CODEC)).checkedGet();
        AgentRecord record = new AgentRecord(agents);
        return record;
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
