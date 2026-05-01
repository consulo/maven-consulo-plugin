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
public class LocalizationJarProcessor implements JarProcessor<LocalizationJarProcessor.Session> {
    public static final String LOCALIZATION_LIB_FOLDER = LocalizeGeneratorMojo.LOCALIZE_LIB + "/";
    public static final String YAML_EXT = ".yaml";

    private record LocalizationKey(String locale, String localizationId) {
    }

    private record RawEntry(
        String jarEntryPath,
        String locale,
        String localizationId,
        boolean isSubFile,
        String subKey,
        byte[] data
    ) {
    }

    public class Session implements JarProcessorSession {
        private final List<RawEntry> myEntries = new ArrayList<>();

        @Override
        public void visit(String jarEntryPath, Supplier<byte[]> dataRequestor) {
            if (!jarEntryPath.startsWith(LOCALIZATION_LIB_FOLDER)) {
                return;
            }

            String rest = jarEntryPath.substring(LOCALIZATION_LIB_FOLDER.length());
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
                String localizationId = afterLocale.substring(0, afterLocale.length() - YAML_EXT.length());
                myEntries.add(new RawEntry(jarEntryPath, locale, localizationId, false, null, dataRequestor.get()));
            }
            else {
                String localizationId = afterLocale.substring(0, subSlash);
                String subPath = afterLocale.substring(subSlash + 1);

                int dot = subPath.lastIndexOf('.');
                if (dot != -1) {
                    subPath = subPath.substring(0, dot);
                }

                String subKey = subPath.replace('\\', '/').replace('/', '.').toLowerCase(Locale.ROOT);

                myEntries.add(new RawEntry(jarEntryPath, locale, localizationId, true, subKey, dataRequestor.get()));
            }
        }

        @Override
        public void close() {
            Map<LocalizationKey, Map<String, String>> textsByKey = new LinkedHashMap<>();
            Map<LocalizationKey, String> mainSourcePath = new HashMap<>();

            for (RawEntry entry : myEntries) {
                LocalizationKey key = new LocalizationKey(entry.locale(), entry.localizationId());
                Map<String, String> texts = textsByKey.computeIfAbsent(key, k -> new LinkedHashMap<>());

                if (entry.isSubFile()) {
                    String text = new String(entry.data(), StandardCharsets.UTF_8);
                    if (texts.put(entry.subKey(), text) != null) {
                        throw new IllegalStateException(
                            "Duplicate localization key '" + entry.subKey() + "'"
                                + " for " + entry.locale() + "/" + entry.localizationId()
                                + " (entry: " + entry.jarEntryPath() + ")"
                        );
                    }
                }
                else {
                    String prev = mainSourcePath.put(key, entry.jarEntryPath());
                    if (prev != null) {
                        throw new IllegalStateException(
                            "Duplicate main YAML for " + entry.locale() + "/" + entry.localizationId() + ": " +
                                prev + " and " + entry.jarEntryPath()
                        );
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
                            throw new IllegalStateException(
                                "Duplicate localization key '" + yamlKey + "'"
                                    + " for " + entry.locale() + "/" + entry.localizationId()
                                    + " (entry: " + entry.jarEntryPath() + ")"
                            );
                        }
                    }
                }
            }

            myEntries.clear();

            for (Map.Entry<LocalizationKey, Map<String, String>> entry : textsByKey.entrySet()) {
                LocalizationKey key = entry.getKey();

                Localize.Builder localizationBuilder = Localize.newBuilder()
                    .setId(key.localizationId())
                    .setLocale(key.locale());

                for (Map.Entry<String, String> txt : entry.getValue().entrySet()) {
                    localizationBuilder.addTexts(Text.newBuilder().setId(txt.getKey()).setText(txt.getValue()));
                }

                Localize localization = localizationBuilder.build();
                if (myLocalizations.putIfAbsent(key, localization) != null) {
                    throw new IllegalStateException(
                        "Duplicate localization across jars: locale=" + key.locale() + ", id=" + key.localizationId()
                    );
                }
            }
        }
    }

    private Map<LocalizationKey, Localize> myLocalizations = new HashMap<>();

    @Override
    public void write(BiConsumer<String, byte[]> consumer) throws IOException {
        if (myLocalizations.isEmpty()) {
            return;
        }

        LocalizeIndex.Builder indexBuilder = LocalizeIndex.newBuilder()
            .setVersion(1);

        for (Localize localization : myLocalizations.values()) {
            indexBuilder.addLocalizes(localization);
        }

        consumer.accept("localize-index.bin", indexBuilder.build().toByteArray());
    }

    @Override
    public Session newSession(String jarName) {
        return new Session();
    }
}
