package com.proofpoint.galaxy.coordinator;

import com.proofpoint.log.Logger;
import org.sonatype.aether.transfer.AbstractTransferListener;
import org.sonatype.aether.transfer.TransferCancelledException;
import org.sonatype.aether.transfer.TransferEvent;

class AetherTransferListener
        extends AbstractTransferListener
{
    private static final Logger log = Logger.get(AetherTransferListener.class);
    private final ThreadLocal<Long> last = new ThreadLocal<Long>();

    @Override
    public void transferInitiated(TransferEvent event)
            throws TransferCancelledException
    {
        log.info("Downloading %s%s...", event.getResource().getRepositoryUrl(), event.getResource().getResourceName());
    }

    @Override
    public void transferSucceeded(TransferEvent event)
    {
        log.info("Downloaded [%s bytes] %s%s", event.getTransferredBytes(), event.getResource().getRepositoryUrl(), event.getResource().getResourceName());
    }

    @Override
    public void transferFailed(TransferEvent event)
    {
        log.error("Failed to download %s%s: %s", event.getResource().getRepositoryUrl(), event.getResource().getResourceName(), event.getException().getMessage());
    }

    @Override
    public void transferProgressed(TransferEvent event)
            throws TransferCancelledException
    {
        Long last = this.last.get();
        if (last == null || last < System.currentTimeMillis() - 5 * 1000) {
            String progress;
            if (event.getResource().getContentLength() > 0) {
                progress = (int) (event.getTransferredBytes() * 100.0 / event.getResource().getContentLength()) + "%";
            }
            else {
                progress = event.getTransferredBytes() + " bytes";
            }
            log.info("Downloading [%s] %s%s...", progress, event.getResource().getRepositoryUrl(), event.getResource().getResourceName());
            this.last.set(System.currentTimeMillis());
        }
    }
}
