package consulo.maven.packaging.processing;

import consulo.maven.generating.LocalizeGeneratorMojo;
import consulo.maven.protobuf.LocalizeProto.Localize;
import consulo.maven.protobuf.LocalizeProto.LocalizeIndex;
import consulo.maven.protobuf.LocalizeProto.Text;
import org.yaml.snakeyaml.Yaml;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

/**
 * @author VISTALL
 * @since 2026-04-25
 */
public class LocalizeJarProcessor implements JarProcessor<LocalizeJarProcessor.Session> {
    public static final String LOCALIZE_LIB = LocalizeGeneratorMojo.LOCALIZE_LIB;
    public static final String YAML_EXT = ".yaml";

    private record LocalizeKey(String locale, String localizeId) {
    }

    private record RawEntry(String jarEntryPath,
                            String locale,
                            String localizeId,
                            boolean isSubFile,
                            String subKey,
                            byte[] data) {
    }

    public class Session implements JarProcessorSession {
        private final List<RawEntry> myEntries = new ArrayList<>();

        @Override
        public void visit(String jarEntryPath, Supplier<byte[]> dataRequestor) {
            if (!jarEntryPath.startsWith(LOCALIZE_LIB + "/")) {
                return;
            }

            String rest = jarEntryPath.substring(LOCALIZE_LIB.length() + 1);
            int slash = rest.indexOf('/');
            if (slash <= 0) {
                return;
            }

            String locale = rest.substring(0, slash);
            String afterLocale = rest.substring(slash + 1);

            int subSlash = afterLocale.indexOf('/');
            if (subSlash == -1) {
                if (!afterLocale.endsWith(YAML_EXT)) {
                    return;
                }
                String localizeId = afterLocale.substring(0, afterLocale.length() - YAML_EXT.length());
                myEntries.add(new RawEntry(jarEntryPath, locale, localizeId, false, null, dataRequestor.get()));
            }
            else {
                String localizeId = afterLocale.substring(0, subSlash);
                String subPath = afterLocale.substring(subSlash + 1);

                int dot = subPath.lastIndexOf('.');
                if (dot != -1) {
                    subPath = subPath.substring(0, dot);
                }

                String subKey = subPath.replace('\\', '/').replace('/', '.').toLowerCase(Locale.ROOT);

                myEntries.add(new RawEntry(jarEntryPath, locale, localizeId, true, subKey, dataRequestor.get()));
            }
        }

        @Override
        public void close() {
            Map<LocalizeKey, Map<String, String>> textsByKey = new LinkedHashMap<>();
            Map<LocalizeKey, String> mainSourcePath = new HashMap<>();

            for (RawEntry entry : myEntries) {
                LocalizeKey key = new LocalizeKey(entry.locale(), entry.localizeId());
                Map<String, String> texts = textsByKey.computeIfAbsent(key, k -> new LinkedHashMap<>());

                if (entry.isSubFile()) {
                    String text = new String(entry.data(), StandardCharsets.UTF_8);
                    if (texts.put(entry.subKey(), text) != null) {
                        throw new IllegalStateException("Duplicate localize key '" + entry.subKey()
                            + "' for " + entry.locale() + "/" + entry.localizeId()
                            + " (entry: " + entry.jarEntryPath() + ")");
                    }
                }
                else {
                    String prev = mainSourcePath.put(key, entry.jarEntryPath());
                    if (prev != null) {
                        throw new IllegalStateException("Duplicate main YAML for " + entry.locale() + "/"
                            + entry.localizeId() + ": " + prev + " and " + entry.jarEntryPath());
                    }

                    Map<String, Map<String, Object>> data;
                    try (Reader reader = new InputStreamReader(new ByteArrayInputStream(entry.data()), StandardCharsets.UTF_8)) {
                        data = new Yaml().load(reader);
                    }
                    catch (Exception e) {
                        throw new IllegalStateException("Failed to parse: " + entry.jarEntryPath(), e);
                    }

                    if (data == null) {
                        continue;
                    }

                    for (Map.Entry<String, Map<String, Object>> kv : data.entrySet()) {
                        String yamlKey = kv.getKey().toLowerCase(Locale.ROOT);

                        String text = "";
                        Map<String, Object> valueMap = kv.getValue();
                        if (valueMap != null) {
                            Object t = valueMap.get("text");
                            if (t != null) {
                                text = t.toString();
                            }
                        }

                        if (texts.put(yamlKey, text) != null) {
                            throw new IllegalStateException("Duplicate localize key '" + yamlKey
                                + "' for " + entry.locale() + "/" + entry.localizeId()
                                + " (entry: " + entry.jarEntryPath() + ")");
                        }
                    }
                }
            }

            myEntries.clear();

            for (Map.Entry<LocalizeKey, Map<String, String>> entry : textsByKey.entrySet()) {
                LocalizeKey key = entry.getKey();

                Localize.Builder localizeBuilder = Localize.newBuilder();
                localizeBuilder.setId(key.localizeId());
                localizeBuilder.setLocale(key.locale());

                for (Map.Entry<String, String> txt : entry.getValue().entrySet()) {
                    Text.Builder textBuilder = Text.newBuilder();
                    textBuilder.setId(txt.getKey());
                    textBuilder.setText(txt.getValue());
                    localizeBuilder.addTexts(textBuilder);
                }

                if (myLocalizes.putIfAbsent(key, localizeBuilder.build()) != null) {
                    throw new IllegalStateException("Duplicate localize across jars: locale=" + key.locale()
                        + ", id=" + key.localizeId());
                }
            }
        }
    }

    private Map<LocalizeKey, Localize> myLocalizes = new HashMap<>();

    @Override
    public void write(BiConsumer<String, byte[]> consumer) throws IOException {
        if (myLocalizes.isEmpty()) {
            return;
        }

        LocalizeIndex.Builder indexBuilder = LocalizeIndex.newBuilder();
        indexBuilder.setVersion(1);

        for (Localize localize : myLocalizes.values()) {
            indexBuilder.addLocalizes(localize);
        }

        consumer.accept("localize-index.bin", indexBuilder.build().toByteArray());
    }

    @Override
    public Session newSession(String jarName) {
        return new Session();
    }
}
