package com.proofpoint.galaxy.cli;

public enum Column
{
    shortId("uuid"),
    uuid,
    ip,
    host,
    status,
    binary,
    config,
    expectedStatus("expected-status"),
    expectedBinary("expected-binary"),
    expectedConfig("expected-config"),
    statusMessage(""),
    location,
    instanceType("type");


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
