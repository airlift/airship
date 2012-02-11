package com.proofpoint.galaxy.cli;

public enum Column
{
    shortId("uuid"),
    uuid,
    internalIp("ip"),
    internalHost("host"),
    externalHost("host"),
    status,
    binary,
    config,
    expectedStatus("expected-status"),
    expectedBinary("expected-binary"),
    expectedConfig("expected-config"),
    statusMessage(""),
    location,
    instanceType("type"),
    internalUri("uri"),
    externalUri("uri");


    private final String header;

    Column()
    {
        this.header = name();
    }

    Column(String header)
    {
        this.header = header;
    }

    public String getHeader()
    {
        return header;
    }
}
