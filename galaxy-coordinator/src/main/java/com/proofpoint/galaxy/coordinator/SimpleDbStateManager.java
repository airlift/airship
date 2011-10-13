package com.proofpoint.galaxy.coordinator;

import com.amazonaws.services.simpledb.AmazonSimpleDB;
import com.amazonaws.services.simpledb.model.Attribute;
import com.amazonaws.services.simpledb.model.CreateDomainRequest;
import com.amazonaws.services.simpledb.model.Item;
import com.amazonaws.services.simpledb.model.PutAttributesRequest;
import com.amazonaws.services.simpledb.model.ReplaceableAttribute;
import com.amazonaws.services.simpledb.model.SelectRequest;
import com.amazonaws.services.simpledb.model.SelectResult;
import com.google.common.base.Preconditions;
import com.proofpoint.galaxy.shared.Assignment;
import com.proofpoint.galaxy.shared.SlotLifecycleState;
import com.proofpoint.log.Logger;
import com.proofpoint.node.NodeInfo;

import javax.inject.Inject;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import static com.google.common.collect.Lists.newArrayList;

public class SimpleDbStateManager implements StateManager
{
    private static final Logger log = Logger.get(SimpleDbStateManager.class);
    private final AmazonSimpleDB simpleDb;
    private final String domainName;

    @Inject
    public SimpleDbStateManager(AmazonSimpleDB simpleDb, NodeInfo nodeInfo)
    {
        this.simpleDb = simpleDb;
        domainName = "galaxy-" + nodeInfo.getEnvironment();
        simpleDb.createDomain(new CreateDomainRequest(domainName));

    }

    @Override
    public Collection<ExpectedSlotStatus> getAllExpectedStates()
    {
        List<ExpectedSlotStatus> slots = newArrayList();
        try {
            String query = String.format("select itemName, state, binary, config from `%s`", domainName);
            SelectResult select = simpleDb.select(new SelectRequest(query, true));
            for (Item item : select.getItems()) {
                ExpectedSlotStatus expectedSlotStatus = loadSlotStatus(item);
                if (expectedSlotStatus != null) {
                    slots.add(expectedSlotStatus);
                }
            }
        }
        catch (Exception e) {
            log.error(e, "Error reading expected slot status");
        }
        return slots;
    }

    @Override
    public void setExpectedState(ExpectedSlotStatus slotStatus)
    {
        Preconditions.checkNotNull(slotStatus, "slotStatus is null");

        List<ReplaceableAttribute> attributes = newArrayList();
        attributes.add(new ReplaceableAttribute("state", slotStatus.getStatus().toString(), true));
        if (slotStatus.getAssignment() != null) {
            attributes.add(new ReplaceableAttribute("binary", slotStatus.getAssignment().getBinary().toString(), true));
            attributes.add(new ReplaceableAttribute("config", slotStatus.getAssignment().getConfig().toString(), true));
        }

        try {
            simpleDb.putAttributes(new PutAttributesRequest().withDomainName(domainName).withItemName(slotStatus.getId().toString()).withAttributes(attributes));
        }
        catch (Exception e) {
            log.error(e, "Error writing expected slot status");
        }
    }

    private ExpectedSlotStatus loadSlotStatus(Item item)
    {
        String id = item.getName();

        String state = null;
        String binary = null;
        String config = null;
        for (Attribute attribute : item.getAttributes()) {
            if ("state".equals(attribute.getName())) {
                state = attribute.getValue();
            } else if ("binary".equals(attribute.getName())) {
                binary = attribute.getValue();
            } else if ("config".equals(attribute.getName())) {
                config = attribute.getValue();
            }
        }

        // just return null for corrupted entries... these will be marked as unexpected
        // and someone will resolve the conflict (and overwrite the corrupted record)

        if (id == null || state == null) {
            return null;
        }
        try {
            if (binary == null || config == null) {
                return new ExpectedSlotStatus(UUID.fromString(id), SlotLifecycleState.valueOf(state), null);
            } else {
                return new ExpectedSlotStatus(UUID.fromString(id), SlotLifecycleState.valueOf(state), new Assignment(binary, config));
            }
        }
        catch (IllegalArgumentException e) {
            return null;
        }
    }
}
