package consulo.maven.packaging.processing.xml;

import maven.bnf.consulo.util.lang.StringUtil;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import java.util.regex.Pattern;

/**
 * @author UNV
 * @since 2026-05-11
 */
public class SvgDimensionsHandler extends DefaultHandler {
    private static final Pattern VIEW_BOX_DELIM = Pattern.compile("(?:,\\s*|\\s+)");

    private double myWidth = -1, myHeight = -1;

    @Override
    public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException {
        if (!"svg".equals(qName)) {
            return;
        }

        myWidth = parseDimension(attributes.getValue("width"), myWidth);
        myHeight = parseDimension(attributes.getValue("height"), myHeight);

        if (myWidth < 0 || myHeight < 0) {
            String[] viewBoxRaw = VIEW_BOX_DELIM.split(attributes.getValue("viewBox"), 4);
            if (viewBoxRaw.length < 4) {
                return;
            }

            myWidth = parseDimension(viewBoxRaw[2], myWidth);
            myHeight = parseDimension(viewBoxRaw[3], myHeight);
        }
    }

    private static double parseDimension(String value, double defaultValue) {
        if (!StringUtil.isNotEmpty(value)) {
            return defaultValue;
        }
        if (value.endsWith("px")) {
            return Double.parseDouble(value.substring(0, value.length() - 2));
        }
        return Double.parseDouble(value);
    }

    public double getWidth() {
        return myWidth;
    }

    public double getHeight() {
        return myHeight;
    }
}
