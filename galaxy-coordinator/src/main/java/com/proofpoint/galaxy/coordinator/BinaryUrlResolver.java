package com.proofpoint.galaxy.coordinator;

import com.proofpoint.galaxy.shared.BinarySpec;
import com.proofpoint.http.server.HttpServerInfo;

import javax.inject.Inject;
import javax.ws.rs.core.UriBuilder;
import java.net.URI;

public class BinaryUrlResolver
{
    private final BinaryRepository repository;
    private final URI baseUri;

    @Inject
    public BinaryUrlResolver(BinaryRepository repository, HttpServerInfo serverInfo)
    {
        this(repository, serverInfo.getHttpUri());
    }

    public BinaryUrlResolver(BinaryRepository repository, URI baseUri)
    {
        this.repository = repository;
        this.baseUri = baseUri;
    }

    public URI resolve(BinarySpec spec)
    {
        URI uri = repository.getBinaryUri(spec);

        if (baseUri != null && uri != null && "file".equalsIgnoreCase(uri.getScheme())) {
            UriBuilder uriBuilder = UriBuilder.fromUri(baseUri)
                    .path(BinaryResource.class)
                    .path(spec.getGroupId())
                    .path(spec.getArtifactId())
                    .path(spec.getVersion())
                    .path(spec.getPackaging());

            if (spec.getClassifier() != null) {
                uriBuilder = uriBuilder.path(spec.getClassifier());
            }

            uri = uriBuilder.build();
        }

        return uri;
    }
}
