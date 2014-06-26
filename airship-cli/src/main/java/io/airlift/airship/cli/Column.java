package io.airlift.airship.cli;

public enum Column
{
    shortId("uuid"),
    uuid,
    machine("machine"),
    internalIp("ip"),
    internalHost("host"),
    externalHost("host"),
    status,
    binary,
    shortBinary("binary"),
    config,
    shortConfig("config"),
    expectedStatus("expected-status"),
    expectedBinary("expected-binary"),
    expectedConfig("expected-config"),
    statusMessage(""),
    location,
    shortLocation("location"),
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
