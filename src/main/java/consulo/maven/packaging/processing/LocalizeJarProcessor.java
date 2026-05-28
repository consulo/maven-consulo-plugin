package consulo.maven.packaging.processing;

import consulo.maven.generating.LocalizeGeneratorMojo;
import consulo.maven.jar.JarEntrySupplier;
import consulo.maven.protobuf.BuildIndexCache;
import consulo.maven.protobuf.LocalizeProto.Localize;
import consulo.maven.protobuf.LocalizeProto.LocalizeIndex;
import consulo.maven.protobuf.LocalizeProto.Text;
import org.yaml.snakeyaml.Yaml;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;

/**
 * @author VISTALL
 * @author UNV
 * @since 2026-04-25
 */
public class LocalizeJarProcessor implements JarProcessor {
    public static final String LOCALIZATION_LIB_FOLDER = LocalizeGeneratorMojo.LOCALIZE_LIB + "/";
    public static final String YAML_EXT = ".yaml";

    private record LocalizationKey(String locale, String id) implements Comparable<LocalizationKey> {
        @Override
        public int compareTo(LocalizationKey that) {
            int result = locale().compareTo(that.locale());
            if (result != 0) {
                return result;
            }
            return id().compareTo(that.id());
        }

        public String toPath() {
            return locale() + "/" + id();
        }
    }

    class Session implements JarProcessorSession {
        private final Map<LocalizationKey, SortedMap<String, String>> myTextsByKey = new LinkedHashMap<>();
        private final Map<LocalizationKey, String> myYamlSourcePath = new HashMap<>();

        private Map<LocalizationKey, Localize> myLocalizationsCache = null;
        private LocalizationKey myLastTextKey = null;
        private SortedMap<String, String> myLastTextMap = null;

        @Override
        public void visit(JarEntrySupplier jarEntrySupplier) {
            String jarEntryPath = jarEntrySupplier.getEntryPath();
            myLocalizationsCache = null;

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

                LocalizationKey key = new LocalizationKey(locale, afterLocale.substring(0, afterLocale.length() - YAML_EXT.length()));

                String prev = myYamlSourcePath.put(key, jarEntryPath);
                if (prev != null) {
                    throw new IllegalStateException("Duplicate YAML for " + key.toPath() + ": " + prev + " and " + jarEntryPath);
                }

                Map<String, Map<String, Object>> data;
                try (Reader reader = new InputStreamReader(new ByteArrayInputStream(jarEntrySupplier.get()), StandardCharsets.UTF_8)) {
                    data = new Yaml().load(reader);
                }
                catch (Exception e) {
                    throw new IllegalStateException("Failed to parse: " + jarEntryPath, e);
                }

                if (data == null) {
                    return;
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

                    addText(jarEntryPath, key, yamlKey, text);
                }
            }
            else {
                LocalizationKey key = new LocalizationKey(locale, afterLocale.substring(0, subSlash));
                String subPath = afterLocale.substring(subSlash + 1);

                int dot = subPath.lastIndexOf('.');
                if (dot != -1) {
                    subPath = subPath.substring(0, dot);
                }

                String subKey = subPath.replace('\\', '/').replace('/', '.').toLowerCase(Locale.ROOT);

                addText(jarEntryPath, key, subKey, new String(jarEntrySupplier.get(), StandardCharsets.UTF_8));
            }
        }

        @Override
        public void loadFrom(BuildIndexCache.JarCache jarCache) {
            myLocalizationsCache = null;
            for (Localize localization : jarCache.getLocalizationsList()) {
                LocalizationKey key = new LocalizationKey(localization.getLocale(), localization.getId());
                for (Text text : localization.getTextsList()) {
                    addText(null, key, text.getId(), text.getText());
                }
            }
        }

        @Override
        public void storeTo(BuildIndexCache.JarCache.Builder jarCacheBuilder) {
            jarCacheBuilder.addAllLocalizations(toLocalizationsMap().values());
        }

        @Override
        public void close() {
            for (Map.Entry<LocalizationKey, Localize> entry : toLocalizationsMap().entrySet()) {
                LocalizationKey key = entry.getKey();
                if (myLocalizations.putIfAbsent(key, entry.getValue()) != null) {
                    throw new IllegalStateException("Duplicate localization across jars: " + key);
                }
            }
        }

        private void addText(String jarEntryPath, LocalizationKey key, String id, String text) {
            if (key != myLastTextKey) {
                myLastTextKey = key;
                myLastTextMap = myTextsByKey.computeIfAbsent(key, k -> new TreeMap<>());
            }

            if (myLastTextMap.put(id, text) != null) {
                throw new IllegalStateException(
                    "Duplicate localization key '" + id + "' for " + key.toPath() + " (entry: " + jarEntryPath + ")"
                );
            }
        }

        private Map<LocalizationKey, Localize> toLocalizationsMap() {
            if (myLocalizationsCache != null) {
                return myLocalizationsCache;
            }

            myLocalizationsCache = new TreeMap<>();
            for (Map.Entry<LocalizationKey, SortedMap<String, String>> entry : myTextsByKey.entrySet()) {
                LocalizationKey key = entry.getKey();

                Localize.Builder localizationBuilder = Localize.newBuilder()
                    .setId(key.id())
                    .setLocale(key.locale());

                for (Map.Entry<String, String> txt : entry.getValue().entrySet()) {
                    localizationBuilder.addTexts(Text.newBuilder().setId(txt.getKey()).setText(txt.getValue()));
                }

                myLocalizationsCache.put(key, localizationBuilder.build());
            }
            return myLocalizationsCache;
        }
    }

    private Map<LocalizationKey, Localize> myLocalizations = new ConcurrentHashMap<>();

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
