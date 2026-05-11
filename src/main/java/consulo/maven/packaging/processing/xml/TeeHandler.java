package consulo.maven.packaging.processing.xml;

import org.xml.sax.Attributes;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import java.util.Arrays;
import java.util.List;

/**
 * @author UNV
 * @since 2026-05-11
 */
public class TeeHandler extends DefaultHandler {
    private final List<DefaultHandler> myHandlers;

    public TeeHandler(DefaultHandler... handlers) {
        myHandlers = Arrays.asList(handlers);
    }

    @Override
    public void setDocumentLocator(Locator locator) {
        for (DefaultHandler handler : myHandlers) {
            handler.setDocumentLocator(locator);
        }
    }

    @Override
    public void startDocument() throws SAXException {
        for (DefaultHandler handler : myHandlers) {
            handler.startDocument();
        }
    }

    @Override
    public void endDocument() throws SAXException {
        for (DefaultHandler handler : myHandlers) {
            handler.endDocument();
        }
    }

    @Override
    public void startPrefixMapping(String prefix, String uri) throws SAXException {
        for (DefaultHandler handler : myHandlers) {
            handler.startPrefixMapping(prefix, uri);
        }
    }

    @Override
    public void endPrefixMapping(String prefix) throws SAXException {
        for (DefaultHandler handler : myHandlers) {
            handler.endPrefixMapping(prefix);
        }
    }

    @Override
    public void startElement(String uri, String localName, String qName, Attributes atts) throws SAXException {
        for (DefaultHandler handler : myHandlers) {
            handler.startElement(uri, localName, qName, atts);
        }
    }

    @Override
    public void endElement(String uri, String localName, String qName) throws SAXException {
        for (DefaultHandler handler : myHandlers) {
            handler.endElement(uri, localName, qName);
        }
    }

    @Override
    public void characters(char[] ch, int start, int length) throws SAXException {
        for (DefaultHandler handler : myHandlers) {
            handler.characters(ch, start, length);
        }
    }

    @Override
    public void ignorableWhitespace(char[] ch, int start, int length) throws SAXException {
        for (DefaultHandler handler : myHandlers) {
            handler.ignorableWhitespace(ch, start, length);
        }
    }

    @Override
    public void processingInstruction(String target, String data) throws SAXException {
        for (DefaultHandler handler : myHandlers) {
            handler.processingInstruction(target, data);
        }
    }

    @Override
    public void skippedEntity(String name) throws SAXException {
        for (DefaultHandler handler : myHandlers) {
            handler.skippedEntity(name);
        }
    }
}