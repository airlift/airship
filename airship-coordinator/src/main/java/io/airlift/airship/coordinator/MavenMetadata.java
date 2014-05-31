package io.airlift.airship.coordinator;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;
import javax.xml.transform.stream.StreamSource;

import java.io.File;
import java.io.InputStream;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

@XmlType
@XmlAccessorType(XmlAccessType.FIELD)
@XmlRootElement(name = "metadata")
public class MavenMetadata
{
    @XmlElement
    public String groupId;

    @XmlElement
    public String artifactId;

    @XmlElement
    public String version;

    @XmlElement
    public Versioning versioning;

    @XmlType
    @XmlAccessorType(XmlAccessType.FIELD)
    public static class Versioning
    {
        @XmlElement
        public Snapshot snapshot;

        @XmlElement
        public String latest;

        @XmlElement
        public String release;

        @XmlElement
        public String lastUpdated;

        @XmlElement(name = "snapshotVersion")
        @XmlElementWrapper(name = "snapshotVersions")
        public final List<SnapshotVersion> snapshotVersions = new ArrayList<SnapshotVersion>();

        @XmlElement(name = "version")
        @XmlElementWrapper(name = "versions")
        public final List<String> versions = new ArrayList<String>();
    }

    @XmlType
    @XmlAccessorType(XmlAccessType.FIELD)
    public static class Snapshot
    {
        @XmlElement
        public String timestamp;

        @XmlElement
        public String buildNumber;
    }

    @XmlType
    @XmlAccessorType(XmlAccessType.FIELD)
    public static class SnapshotVersion
    {
        @XmlElement
        public String classifier;

        @XmlElement
        public String extension;

        @XmlElement
        public String value;

        @XmlElement
        public String updated;
    }


    private static final JAXBContext jaxbContext;

    static {
        try {
            jaxbContext = JAXBContext.newInstance(MavenMetadata.class);
        }
        catch (JAXBException e) {
            throw new RuntimeException(e);
        }
    }

    public static MavenMetadata unmarshalMavenMetadata(InputStream in)
            throws Exception
    {
        Unmarshaller unmarshaller = jaxbContext.createUnmarshaller();
        return unmarshaller.unmarshal(new StreamSource(in), MavenMetadata.class).getValue();
    }

    public static MavenMetadata unmarshalMavenMetadata(String in)
            throws Exception
    {
        Unmarshaller unmarshaller = jaxbContext.createUnmarshaller();
        return unmarshaller.unmarshal(new StreamSource(new StringReader(in)), MavenMetadata.class).getValue();
    }

    public static void marshalMavenMetadata(File file, MavenMetadata metadata)
            throws Exception
    {
        file.getParentFile().mkdirs();

        Marshaller marshaller = jaxbContext.createMarshaller();
        marshaller.marshal(metadata, file);
    }

}
