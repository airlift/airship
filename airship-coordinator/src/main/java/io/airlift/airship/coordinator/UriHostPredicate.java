package io.airlift.airship.coordinator;

import com.google.common.base.Predicate;
import com.google.common.net.InetAddresses;

import javax.annotation.Nullable;

import java.net.InetAddress;
import java.net.URI;

public class UriHostPredicate implements Predicate<URI>
{
    private final Predicate<CharSequence> predicate;

    public UriHostPredicate(String hostGlob)
    {
        predicate = new GlobPredicate(hostGlob.toLowerCase());
    }

    @Override
    public boolean apply(@Nullable URI uri)
    {
        if (uri == null) {
            return false;
        }

        String host = uri.getHost();
        if (host == null) {
            return false;
        }

        // match host string directly
        if (predicate.apply(host.toLowerCase())) {
            return true;
        }

        // lookup ip and apply to ip string
        try {
            InetAddress inetAddress = InetAddress.getByName(host);
            if (predicate.apply(InetAddresses.toAddrString(inetAddress))) {
                return true;
            }
        }
        catch (Exception ignored) {
        }
        return false;
    }
}
