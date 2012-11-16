package io.airlift.airship.coordinator.auth;

import com.google.common.base.Charsets;
import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import io.airlift.units.Duration;
import org.apache.commons.codec.binary.Base64;

import javax.inject.Inject;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static java.lang.Math.abs;
import static java.lang.String.format;
import static java.lang.System.currentTimeMillis;
import static javax.ws.rs.core.Response.Status.BAD_REQUEST;
import static javax.ws.rs.core.Response.Status.FORBIDDEN;
import static org.apache.commons.codec.digest.DigestUtils.md5Hex;

public class AuthFilter
        implements Filter
{
    public static final String AUTHORIZED_KEY_ATTRIBUTE = "AuthorizedKey";
    private static final Duration MAX_REQUEST_TIME_SKEW = new Duration(5, TimeUnit.MINUTES);

    private final SignatureVerifier verifier;
    private final boolean enabled;

    @Inject
    public AuthFilter(AuthConfig config, SignatureVerifier verifier)
    {
        this.verifier = verifier;
        this.enabled = config.isEnabled();
    }

    @Override
    public void init(FilterConfig filterConfig)
            throws ServletException
    {
    }

    /**
     * Verify authorization header:
     * <pre>
     * Authorization: Airship fingerprint:signature
     * fingerprint = hex md5 of private key
     * signature = base64 signature of [ts, method, uri, bodyMd5]
     * </pre>
     */
    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain chain)
            throws IOException, ServletException
    {
        if (!enabled) {
            chain.doFilter(servletRequest, servletResponse);
            return;
        }

        HttpServletRequest request = (HttpServletRequest) servletRequest;
        HttpServletResponse response = (HttpServletResponse) servletResponse;

        // get authorization headers
        ArrayList<String> authorizations = Collections.list(request.getHeaders("Authorization"));
        if (authorizations.isEmpty()) {
            sendError(response, BAD_REQUEST, "Missing Authorization header");
            return;
        }

        //
        // Generate message
        //

        // get unix timestamp from request time
        long millis;
        try {
            millis = request.getDateHeader("Date");
        }
        catch (IllegalArgumentException e) {
            sendError(response, BAD_REQUEST, "Invalid Date header");
            return;
        }
        if (millis == -1) {
            sendError(response, BAD_REQUEST, "Missing Date header");
            return;
        }
        long serverTime = currentTimeMillis();
        if (abs(serverTime - millis) > MAX_REQUEST_TIME_SKEW.toMillis()) {
            sendError(response, BAD_REQUEST, format("Request time too skewed (server time: %s)", serverTime / 1000));
            return;
        }
        long timestamp = millis / 1000;

        // get method and uri with query parameters
        String method = request.getMethod();
        String uri = getRequestUri(request);

        // wrap request to allow reading body
        RequestWrapper requestWrapper = new RequestWrapper(request);
        String bodyMd5 = md5Hex(requestWrapper.getRequestBody());

        // compute signature payload
        String stringToSign = Joiner.on('\n').join(timestamp, method, uri, bodyMd5);
        byte[] bytesToSign = stringToSign.getBytes(Charsets.UTF_8);

        //
        // try each authorization header
        //
        for (String authorization : authorizations) {

            // parse authorization header
            List<String> authTokens = ImmutableList.copyOf(Splitter.on(' ').omitEmptyStrings().split(authorization));
            if ((authTokens.size() != 2) || (!authTokens.get(0).equals("Airship"))) {
                sendError(response, BAD_REQUEST, "Invalid Authorization header");
                return;
            }
            List<String> authParts = ImmutableList.copyOf(Splitter.on(':').split(authTokens.get(1)));
            if (authParts.size() != 2) {
                sendError(response, BAD_REQUEST, "Invalid Authorization token");
                return;
            }

            // parse authorization token
            String hexFingerprint = authParts.get(0);
            String base64Signature = authParts.get(1);
            Fingerprint fingerprint;
            try {
                fingerprint = Fingerprint.valueOf(hexFingerprint);
            }
            catch (IllegalArgumentException e) {
                sendError(response, BAD_REQUEST, "Invalid Authorization fingerprint");
                return;
            }
            byte[] signature;
            try {
                signature = Base64.decodeBase64(base64Signature);
            }
            catch (Exception e) {
                sendError(response, BAD_REQUEST, "Invalid Authorization signature encoding");
                return;
            }


            // verify signature
            AuthorizedKey authorizedKey = verifier.verify(fingerprint, signature, bytesToSign);
            if (authorizedKey == null) {
                continue;
            }
            request.setAttribute(AUTHORIZED_KEY_ATTRIBUTE, authorizedKey);

            chain.doFilter(requestWrapper, response);
            return;
        }

        sendError(response, FORBIDDEN, "Signature verification failed");
    }

    @Override
    public void destroy()
    {
    }

    private static void sendError(HttpServletResponse response, Response.Status status, String error)
            throws IOException
    {
        response.reset();
        response.setStatus(status.getStatusCode());
        response.setContentType(MediaType.TEXT_PLAIN);
        PrintWriter writer = response.getWriter();
        writer.println(error);
        writer.close();
    }

    private static String getRequestUri(HttpServletRequest request)
    {
        String uri = request.getRequestURI();
        String query = request.getQueryString();
        return (query == null) ? uri : (uri + "?" + query);
    }
}
