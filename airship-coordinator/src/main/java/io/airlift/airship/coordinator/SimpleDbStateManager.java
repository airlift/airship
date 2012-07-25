package io.airlift.airship.coordinator;

import com.amazonaws.AmazonClientException;
import com.amazonaws.services.simpledb.AmazonSimpleDB;
import com.amazonaws.services.simpledb.model.Attribute;
import com.amazonaws.services.simpledb.model.CreateDomainRequest;
import com.amazonaws.services.simpledb.model.DeleteAttributesRequest;
import com.amazonaws.services.simpledb.model.Item;
import com.amazonaws.services.simpledb.model.PutAttributesRequest;
import com.amazonaws.services.simpledb.model.ReplaceableAttribute;
import com.amazonaws.services.simpledb.model.SelectRequest;
import com.amazonaws.services.simpledb.model.SelectResult;
import com.google.common.base.Preconditions;
import io.airlift.airship.shared.Assignment;
import io.airlift.airship.shared.ExpectedSlotStatus;
import io.airlift.airship.shared.SlotLifecycleState;
import com.proofpoint.log.Logger;
import com.proofpoint.node.NodeInfo;

import javax.inject.Inject;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.google.common.collect.Lists.newArrayList;

public class SimpleDbStateManager implements StateManager
{
    private static final Logger log = Logger.get(SimpleDbStateManager.class);
    private final AmazonSimpleDB simpleDb;
    private final String domainName;
    private boolean domainCreated;

    @Inject
    public SimpleDbStateManager(AmazonSimpleDB simpleDb, NodeInfo nodeInfo)
    {
        this.simpleDb = simpleDb;
        domainName = "airship-" + nodeInfo.getEnvironment();
    }

    @Override
    public Collection<ExpectedSlotStatus> getAllExpectedStates()
    {
        List<ExpectedSlotStatus> slots = newArrayList();
        if (isDomainCreated()) {
            try {
                String query = String.format("select itemName, state, binary, config from `%s`", domainName);
                SelectResult select = simpleDb.select(new SelectRequest(query, true));
                for (Item item : select.getItems()) {
                    ExpectedSlotStatus expectedSlotStatus = loadSlotStatus(item);
                    if (expectedSlotStatus != null) {
                        slots.add(expectedSlotStatus);
                    }
                }
                expectedStateStoreUp();
            }
            catch (Exception e) {
                expectedStateStoreDown(e);
            }
        }
        return slots;
    }

    @Override
    public void deleteExpectedState(UUID slotId)
    {
        Preconditions.checkNotNull(slotId, "id is null");

        if (isDomainCreated()) {
            List<Attribute> attributes = newArrayList();
            attributes.add(new Attribute("state", null));
            attributes.add(new Attribute("binary", null));
            attributes.add(new Attribute("config", null));

            try {
                simpleDb.deleteAttributes(new DeleteAttributesRequest().withDomainName(domainName).withItemName(slotId.toString()).withAttributes(attributes));
                expectedStateStoreUp();
            }
            catch (Exception e) {
                expectedStateStoreDown(e);
            }
        }
    }

    @Override
    public void setExpectedState(ExpectedSlotStatus slotStatus)
    {
        Preconditions.checkNotNull(slotStatus, "slotStatus is null");

        if (isDomainCreated()) {
            List<ReplaceableAttribute> attributes = newArrayList();
            attributes.add(new ReplaceableAttribute("state", slotStatus.getStatus().toString(), true));
            if (slotStatus.getAssignment() != null) {
                attributes.add(new ReplaceableAttribute("binary", slotStatus.getAssignment().getBinary(), true));
                attributes.add(new ReplaceableAttribute("config", slotStatus.getAssignment().getConfig(), true));
            }

            try {
                simpleDb.putAttributes(new PutAttributesRequest().withDomainName(domainName).withItemName(slotStatus.getId().toString()).withAttributes(attributes));
                expectedStateStoreUp();
            }
            catch (Exception e) {
                expectedStateStoreDown(e);
            }
        }
    }

    private synchronized boolean isDomainCreated()
    {
        if (!domainCreated) {
            try {
                simpleDb.createDomain(new CreateDomainRequest(domainName));
                domainCreated = true;
            }
            catch (AmazonClientException e) {
                expectedStateStoreDown(e);
            }
        }
        return domainCreated;
    }

    private final AtomicBoolean storeUp = new AtomicBoolean(true);

    private void expectedStateStoreDown(Exception e)
    {
        if (storeUp.compareAndSet(true, false)) {
            log.error(e, "Expected state store is down");
        }
    }

    private void expectedStateStoreUp()
    {
        if (storeUp.compareAndSet(false, true)) {
            log.info("Expected state store is up");
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
            }
            else if ("binary".equals(attribute.getName())) {
                binary = attribute.getValue();
            }
            else if ("config".equals(attribute.getName())) {
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
            }
            else {
                return new ExpectedSlotStatus(UUID.fromString(id), SlotLifecycleState.valueOf(state), new Assignment(binary, config));
            }
        }
        catch (IllegalArgumentException e) {
            return null;
        }
    }
}
