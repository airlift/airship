package io.airlift.airship.coordinator.auth.ssh;

import com.google.common.base.Splitter;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.PeekingIterator;

import java.io.IOException;
import java.math.BigInteger;
import java.security.PrivateKey;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.google.common.collect.ImmutableList.copyOf;
import static com.google.common.collect.Iterators.peekingIterator;
import static com.google.common.io.CharStreams.newReaderSupplier;
import static com.google.common.io.CharStreams.readLines;
import static org.apache.commons.codec.binary.Base64.decodeBase64;

public class PemDecoder
{
    public static PrivateKey decodeSshPrivateKey(String pemData)
            throws IOException
    {
        Pem pem = parsePem(pemData);

        if (pem.getHeaders().containsKey("Proc-Type")) {
            throw new IllegalArgumentException("Encrypted keys are not supported");
        }

        DerReader reader = new DerReader(pem.getData());

        byte[] sequence = reader.readEntry(DerType.SEQUENCE);
        if (!reader.isComplete()) {
            throw new IllegalArgumentException("Invalid ssh key");
        }

        reader = new DerReader(sequence);
        BigInteger version = reader.readBigInteger();
        if (!version.equals(BigInteger.ZERO)) {
            throw new IllegalArgumentException("Unknown ssh key version " + version);
        }

        if (pem.getType().equals("DSA PRIVATE KEY")) {
            BigInteger p = reader.readBigInteger();
            BigInteger q = reader.readBigInteger();
            BigInteger g = reader.readBigInteger();
            BigInteger y = reader.readBigInteger();
            BigInteger x = reader.readBigInteger();

            if (!reader.isComplete()) {
                throw new IllegalArgumentException("Invalid ssh key");
            }

            return new PemDsaPrivateKey(pemData, p, q, g, x);
        }
        else if (pem.getType().equals("RSA PRIVATE KEY")) {
            BigInteger n = reader.readBigInteger();
            BigInteger e = reader.readBigInteger();
            BigInteger d = reader.readBigInteger();

            // rsa key contains several more numbers which we don't need

            return new PemRsaPrivateKey(pemData, d, n);
        }

        throw new IllegalArgumentException("Unknown key type " + pem.getType());
    }


    public static Pem parsePem(String pemData)
            throws IOException
    {
        List<String> lines = readLines(newReaderSupplier(pemData));
        for (PeekingIterator<String> iterator = peekingIterator(lines.iterator()); iterator.hasNext(); ) {
            String line = iterator.next().trim();
            if (line.isEmpty()) {
                continue;
            }
            String type = parseBegin(line);
            return parsePem(type, iterator);
        }
        throw new IllegalArgumentException("Invalid pem data: missing BEGIN");
    }

    public static String parseBegin(String line) {
        Pattern beginPattern = Pattern.compile("-----BEGIN (.*)-----");
        Matcher matcher = beginPattern.matcher(line);
        if (matcher.matches()) {
            return matcher.group(1).trim();
        }
        return null;
    }

    private static Pem parsePem(String type, PeekingIterator<String> iterator)
    {
        if (!iterator.hasNext()) {
            throw new IllegalAccessError("Invalid pem data: missing END");
        }

        String end = "-----END " + type + "-----";

        // read headers
        ListMultimap<String, String> headers = readHeaders(iterator, end);

        // read body
        byte[] body = readBody(iterator, end);

        return new Pem(type, headers, body);
    }

    private static ListMultimap<String, String> readHeaders(PeekingIterator<String> iterator, String keyEnd)
    {
        ListMultimap<String, String> headers = ArrayListMultimap.create();
        for (String line = iterator.peek();
             line != null && !line.startsWith(keyEnd) && line.contains(":");
             line = iterator.peek()) {

            // consume line from iterator
            iterator.next();

            // header name and value are separated by a colon
            List<String> header = copyOf(Splitter.on(':').trimResults().limit(2).split(line));

            String name = header.get(0);

            // values are comma separated
            List<String> values = ImmutableList.of();
            if (header.size() == 2) {
                values = copyOf(Splitter.on(',').trimResults().split(header.get(1)));
            }
            headers.putAll(name, values);
        }
        return headers;
    }

    private static byte[] readBody(PeekingIterator<String> iterator, String keyEnd)
    {
        StringBuilder body = new StringBuilder();
        for (String line = iterator.peek();
             line != null && !line.startsWith(keyEnd);
             line = iterator.peek()) {

            // consume line from iterator
            iterator.next();

            body.append(line.trim());
        }

        return decodeBase64(body.toString());
    }

    private static class Pem
    {
        private final String type;
        private final ListMultimap<String, String> headers;
        private final byte[] data;

        private Pem(String type, ListMultimap<String, String> headers, byte[] data)
        {
            this.type = type;
            this.headers = headers;
            this.data = data;
        }

        public String getType()
        {
            return type;
        }

        public ListMultimap<String, String> getHeaders()
        {
            return headers;
        }

        public byte[] getData()
        {
            return data;
        }

        @Override
        public String toString()
        {
            final StringBuilder sb = new StringBuilder();
            sb.append("Pem");
            sb.append("{type='").append(type).append('\'');
            sb.append(", headers=").append(headers);
            sb.append(", data.length=").append(data.length);
            sb.append('}');
            return sb.toString();
        }
    }
}
