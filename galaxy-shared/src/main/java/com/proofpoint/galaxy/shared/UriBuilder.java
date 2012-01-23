package com.proofpoint.galaxy.shared;

import com.google.common.base.Ascii;
import com.google.common.base.CharMatcher;
import com.google.common.base.Charsets;
import com.google.common.base.Preconditions;
import com.google.common.base.Splitter;
import com.google.common.collect.LinkedListMultimap;
import com.google.common.collect.ListMultimap;
import com.google.common.primitives.Bytes;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.util.Iterator;
import java.util.Map;

import static java.lang.Character.forDigit;

/**
 * An RFC-3986-compatible URI builder with support for http-like query parameters
 */
public class UriBuilder
{
    private String scheme;
    private String host;
    private int port = -1;
    private String path = ""; // decoded path
    private ListMultimap<String, String> params = LinkedListMultimap.create(); // decoded query params

    private static byte[] PCHAR = {
            'a','b','c','d','e','f','g','h','i','j','k','l','m','n','o','p','q','r','s','t','u','v','w','x','y','z',
            'A','B','C','D','E','F','G','H','I','J','K','L','M','N','O','P','Q','R','S','T','U','V','W','X','Y','Z',
            '0','1','2','3','4','5','6','7','8','9',
            '-', '.', '_', '~', '!', '$', '\'', '(', ')', '*', '+', ',', ';', '=', ':', '@',
    };

    private static byte[] ALLOWED_PATH_CHARS = Bytes.concat(PCHAR, new byte[] {'/', '&'});
    private static byte[] ALLOWED_QUERY_CHARS = Bytes.concat(PCHAR, new byte[] {'/', '?'});

    private UriBuilder()
    {
    }

    private UriBuilder(URI previous)
    {
        scheme = previous.getScheme();
        host = previous.getHost();
        port = previous.getPort();
        path = percentDecode(previous.getRawPath());
        params.putAll(parseParams(previous.getRawQuery()));
    }

    public static UriBuilder uriBuilder()
    {
        return new UriBuilder();
    }

    public static UriBuilder uriBuilderFromUri(URI uri)
    {
        Preconditions.checkNotNull(uri, "uri is null");

        return new UriBuilder(uri);
    }

    public UriBuilder scheme(String scheme)
    {
        Preconditions.checkNotNull(scheme, "scheme is null");
        this.scheme = scheme;
        return this;
    }

    public UriBuilder host(String host)
    {
        Preconditions.checkNotNull(host, "host is null");
        this.host = host;
        return this;
    }

    public UriBuilder port(int port)
    {
        Preconditions.checkArgument(port >= 1 && port <= 65535, "port must be in the range 1-65535");
        this.port = port;
        return this;
    }
    
    public UriBuilder defaultPort()
    {
        this.port = -1;
        return this;
    }

    /**
     * Replace the current path with the given unencoded path
     */
    public UriBuilder replacePath(String path)
    {
        Preconditions.checkNotNull(path, "path is null");

        if (!path.equals("") && !path.startsWith("/")) {
            path = "/" + path;
        }

        this.path = path;
        return this;
    }

    /**
     * Append an unencoded path.
     *
     * All reserved characters except '/' will be percent-encoded. '/' are considered as path separators and
     * appended verbatim.
     */
    public UriBuilder appendPath(String path)
    {
        Preconditions.checkNotNull(path, "path is null");

        StringBuilder builder = new StringBuilder(this.path);
        if (!this.path.endsWith("/")) {
            builder.append('/');
        }

        if (path.startsWith("/")) {
            path = path.substring(1);
        }

        builder.append(path);

        this.path = builder.toString();

        return this;
    }

    public UriBuilder replaceParameter(String name, String value)
    {
        Preconditions.checkNotNull(name, "name is null");

        params.removeAll(name);
        params.put(name, value);
        return this;
    }

    public UriBuilder addParameter(String name, String value)
    {
        Preconditions.checkNotNull(name, "name is null");

        params.put(name, value);
        return this;
    }

    // return an RFC-3986-compatible URI
    public String toString()
    {
        StringBuilder builder = new StringBuilder();
        builder.append(scheme);
        builder.append("://");
        builder.append(host);
        if (port != -1) {
            builder.append(':');
            builder.append(port);
        }

        builder.append(encode(path, ALLOWED_PATH_CHARS));

        if (!params.isEmpty()) {
            builder.append('?');

            for (Iterator<Map.Entry<String, String>> iterator = params.entries().iterator(); iterator.hasNext(); ) {
                Map.Entry<String, String> entry = iterator.next();

                builder.append(encode(entry.getKey(), ALLOWED_QUERY_CHARS));
                builder.append('=');
                builder.append(encode(entry.getValue(), ALLOWED_QUERY_CHARS));

                if (iterator.hasNext()) {
                    builder.append('&');
                }
            }
        }

        return builder.toString();
    }

    public URI build()
    {
        Preconditions.checkState(scheme != null, "scheme has not been set");
        Preconditions.checkState(host != null, "host has not been set");
        return URI.create(toString());
    }

    private ListMultimap<String, String> parseParams(String query)
    {
        LinkedListMultimap<String, String> result = LinkedListMultimap.create();

        if (query != null) {
            Iterable<String> pairs = Splitter.on("&")
                    .omitEmptyStrings()
                    .split(query);

            for (String pair : pairs) {
                String[] parts = pair.split("=", 2);
                result.put(percentDecode(parts[0]), percentDecode(parts[1]));
            }
        }

        return result;
    }

    private String encode(String input, byte... allowed)
    {
        StringBuilder builder = new StringBuilder();

        ByteBuffer buffer = Charsets.UTF_8.encode(input);
        while (buffer.remaining() > 0) {
            byte b = buffer.get();

            if (Bytes.contains(allowed, b)) {
                builder.append((char) b); // b is ASCII
            }
            else {
                builder.append('%');
                builder.append(Ascii.toUpperCase(forDigit((b >>> 4) & 0xF, 16)));
                builder.append(Ascii.toUpperCase(forDigit(b & 0xF, 16)));
            }
        }

        return builder.toString();
    }

    /**
     * input must be an ASCII string representing a percent-encoded UTF-8 byte sequence
     */
    private String percentDecode(String encoded)
    {
        Preconditions.checkArgument(CharMatcher.ASCII.matchesAllOf(encoded), "string must be ASCII");

        ByteArrayOutputStream out = new ByteArrayOutputStream(encoded.length());
        for (int i = 0; i < encoded.length(); i++) {
            char c = encoded.charAt(i);

            if (c == '%') {
                Preconditions.checkArgument(i + 2 < encoded.length(), "percent encoded value is truncated");

                char high = encoded.charAt(++i);
                char low = encoded.charAt(++i);
                int value = (Character.digit(high, 16) << 4) | (Character.digit(low, 16));
                out.write(value);
            }
            else {
                out.write((int) c);
            }
        }

        try {
            return Charsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(out.toByteArray()))
                    .toString();
        }
        catch (CharacterCodingException e) {
            throw new IllegalArgumentException("input does not represent a proper UTF8-encoded string");
        }
    }
}
