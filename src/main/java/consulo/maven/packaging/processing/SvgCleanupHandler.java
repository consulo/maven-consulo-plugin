package consulo.maven.packaging.processing;

import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import java.util.ArrayList;
import java.util.Objects;

/**
 * @author UNV
 * @since 2026-05-04
 */
public class SvgCleanupHandler extends DefaultHandler {
    private record Attribute(String qName, String value) {
        public void writeTo(XMLStreamWriter writer) throws XMLStreamException {
            writer.writeAttribute(qName(), value());
        }
    }

    private class CachedAttributes {
        private final ArrayList<Attribute> myAttributes = new ArrayList<>();

        public void cache(Attributes attributes) {
            int length = attributes.getLength();
            myAttributes.clear();
            myAttributes.ensureCapacity(length);
            for (int i = 0; i < length; i++) {
                myAttributes.add(new Attribute(attributes.getQName(i), attributes.getValue(i)));
            }
        }

        public void write() throws XMLStreamException {
            for (Attribute attribute : myAttributes) {
                attribute.writeTo(myWriter);
            }
            myAttributes.clear();
        }
    }

    private final XMLStreamWriter myWriter;
    private final StringBuilder myActiveCharacters = new StringBuilder();
    private String myActiveQName = null;
    private final CachedAttributes myActiveAttributes = new CachedAttributes();

    SvgCleanupHandler(XMLStreamWriter writer) throws XMLStreamException {
        myWriter = writer;
    }

    @Override
    public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException {
        flushCharacters();
        flushActiveElement(false);
        myActiveQName = qName;
        myActiveAttributes.cache(attributes);
    }

    @Override
    public void endElement(String uri, String localName, String qName) throws SAXException {
        try {
            flushCharacters();
            if (Objects.equals(myActiveQName, qName)) {
                flushActiveElement(true);
            }
            else {
                flushActiveElement(false);
                myWriter.writeEndElement();
            }
        } catch (Exception e) {
            throw new SAXException(e);
        }
    }

    @Override
    public void characters(char[] ch, int start, int length) throws SAXException {
        myActiveCharacters.append(ch, start, length);
    }

    @Override
    public void endDocument() throws SAXException {
        try {
            myWriter.flush();
            myWriter.close();
        }
        catch (XMLStreamException e) {
            throw new SAXException(e);
        }
    }

    private void flushCharacters() throws SAXException {
        StringBuilder chars = myActiveCharacters;
        if (chars.isEmpty()) {
            return;
        }

        int start = 0, end = chars.length();

        while (start < end && chars.charAt(start) <= ' ') {
            start++;
        }

        while (start < end && chars.charAt(end - 1) <= ' ') {
            end--;
        }

        if (start >= end) {
            chars.setLength(0);
            return;
        }

        flushActiveElement(false);

        try {
            myWriter.writeCharacters(chars.substring(start, end));
            chars.setLength(0);
        }
        catch (XMLStreamException e) {
            throw new SAXException(e);
        }
    }

    private void flushActiveElement(boolean asEmptyElement) throws SAXException {
        try {
            if (myActiveQName == null) {
                return;
            }

            if (asEmptyElement) {
                myWriter.writeEmptyElement(myActiveQName);
            }
            else {
                myWriter.writeStartElement(myActiveQName);
            }
            myActiveAttributes.write();
            myActiveQName = null;
        } catch (Exception e) {
            throw new SAXException(e);
        }
    }
}
