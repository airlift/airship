package com.proofpoint.galaxy;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableList.Builder;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.inject.Inject;
import com.ning.http.client.AsyncHttpClient;
import com.proofpoint.experimental.json.JsonCodec;
import com.proofpoint.experimental.json.JsonCodecBuilder;
import com.proofpoint.log.Logger;

import javax.annotation.PostConstruct;
import java.net.URI;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class AnnouncementService
{
    private static final Logger log = Logger.get(AnnouncementService.class);
    private static final JsonCodec<AgentStatusRepresentation> codec = new JsonCodecBuilder().build(AgentStatusRepresentation.class);

    private final Agent agent;
    private final URI agentBaseURI;
    private final AsyncHttpClient client;
    private String announcementUrl;
    private ScheduledExecutorService executor;
    private ScheduledFuture<?> announcementTask;

    @Inject
    public AnnouncementService(AgentConfig agentConfig, Agent agent)
    {
        this.agent = agent;
        this.agentBaseURI = agentConfig.getAgentBaseURI();
        this.announcementUrl = agentConfig.getConsoleBaseURI().resolve("v1/agent/" + agent.getAgentId()).toString();
        this.client = new AsyncHttpClient();
        this.executor = Executors.newSingleThreadScheduledExecutor(new ThreadFactoryBuilder().setDaemon(true).setNameFormat("announce-%s").build());
    }

    @PostConstruct
    public synchronized void start()
    {
        // already started?
        if (announcementTask != null) {
            return;
        }

        announcementTask = executor.scheduleWithFixedDelay(new Runnable()
        {
            @Override
            public void run()
            {
                try {
                    announce();
                }
                catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }, 0, 3, TimeUnit.SECONDS);

    }

    public synchronized void stop()
    {
        // already stopped?
        if (announcementTask == null) {
            return;
        }

        // todo delete announcement
        announcementTask.cancel(true);
        announcementTask = null;
    }

    public synchronized void announce()
            throws InterruptedException
    {
        try {
            Builder<SlotStatus> builder = ImmutableList.builder();
            for (SlotManager slotManager : agent.getAllSlots()) {
                SlotStatus slotStatus = slotManager.start();
                builder.add(slotStatus);
            }
            AgentStatus agentStatus = new AgentStatus(agent.getAgentId(), builder.build());
            AgentStatusRepresentation agentStatusRepresentation = AgentStatusRepresentation.from(agentStatus, agentBaseURI);
            String json = codec.toJson(agentStatusRepresentation);

            client.preparePut(announcementUrl)
                    .setBody(json)
                    .execute()
                    .get();
        }
        catch (InterruptedException e) {
            throw e;
        }
        catch (Exception e) {
            log.warn(e, "Error announcing status to " + announcementUrl);
        }
    }
}
