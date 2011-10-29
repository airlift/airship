package com.proofpoint.galaxy.standalone;

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
