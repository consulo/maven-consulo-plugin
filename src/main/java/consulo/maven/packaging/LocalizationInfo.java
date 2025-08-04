package consulo.maven.packaging;

import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Map;
import java.util.TreeMap;

/**
 * @author VISTALL
 * @since 2025-07-30
 */
public class LocalizationInfo {
    public static class LocalizeText {
        private final String myText;

        public LocalizeText(String text) {
            this.myText = text;
        }
    }

    public static final int VERSION = 1;

    private final String myLocale;

    private final Map<String, LocalizeText> myTexts = new TreeMap<>();

    public LocalizationInfo(String locale) {
        this.myLocale = locale;
    }

    public void add(String key, LocalizeText localizeText) {
        myTexts.put(key, localizeText);
    }

    public void write(DataOutputStream stream) throws IOException {
        stream.writeInt(VERSION);
        stream.writeChars(myLocale);
        stream.writeInt(myTexts.size());

        for (Map.Entry<String, LocalizeText> entry : myTexts.entrySet()) {
            String key = entry.getKey();
            LocalizeText value = entry.getValue();

            byte flags = 0;

            stream.writeByte(flags);
            stream.writeChars(key);
            stream.writeChars(value.myText);
        }
    }
}
