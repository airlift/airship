package com.proofpoint.galaxy.coordinator;

import com.proofpoint.galaxy.shared.BinarySpec;
import com.proofpoint.http.server.HttpServerConfig;
import com.proofpoint.http.server.HttpServerInfo;
import com.proofpoint.node.NodeInfo;
import org.testng.annotations.Test;

import java.net.InetAddress;
import java.net.URI;

import static org.testng.Assert.assertEquals;

public class TestHttpBinaryRepository
{
    @Test
    public void testRewritesUri()
            throws Exception
    {
        HttpServerConfig config = new HttpServerConfig();
        NodeInfo nodeInfo = new NodeInfo("testing", "general", "abc", InetAddress.getLocalHost(), "/slot");

        HttpServerInfo serverInfo = new HttpServerInfo(config, nodeInfo);
        BinaryUrlResolver urlResolver = new BinaryUrlResolver(new TestingBinaryRepository(), serverInfo);

        URI uri = urlResolver.resolve(new BinarySpec("food.fruit", "banana", "2.0-SNAPSHOT", "tar.gz", null));

        assertEquals(uri.getScheme(), serverInfo.getHttpUri().getScheme());
        assertEquals(uri.getHost(), serverInfo.getHttpUri().getHost());
        assertEquals(uri.getPort(), serverInfo.getHttpUri().getPort());
        assertEquals(uri.getPath(), "/v1/binary/food.fruit/banana/2.0-SNAPSHOT/tar.gz");
    }
}
