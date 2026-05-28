package consulo.maven.packaging.processing;

import com.google.protobuf.ByteString;
import consulo.maven.jar.JarEntrySupplier;
import consulo.maven.protobuf.BuildIndexCache;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;

/**
 * @author UNV
 * @since 2026-05-28
 */
public class PluginMetaProcessor implements JarProcessor {
    class Session implements JarProcessorSession {
        private final String jarName;
        private final SortedMap<String, BuildIndexCache.MetaFile> myJarMetaData = new TreeMap<>();

        Session(String jarName) {
            this.jarName = jarName;
        }

        @Override
        public void visit(JarEntrySupplier jarEntrySupplier) {
            String jarEntryPath = jarEntrySupplier.getEntryPath();
            if (META_FILES.contains(jarEntryPath)) {
                BuildIndexCache.MetaFile metaFile = BuildIndexCache.MetaFile.newBuilder()
                    .setPath(jarEntryPath)
                    .setContent(ByteString.copyFrom(jarEntrySupplier.get()))
                    .build();
                myJarMetaData.put(jarEntryPath, metaFile);
            }
        }

        @Override
        public void loadFrom(BuildIndexCache.JarCache jarCache) {
            for (BuildIndexCache.MetaFile metaFile : jarCache.getMetaDataList()) {
                myJarMetaData.put(metaFile.getPath(), metaFile);
            }
        }

        @Override
        public void storeTo(BuildIndexCache.JarCache.Builder jarCacheBuilder) {
            jarCacheBuilder.addAllMetaData(myJarMetaData.values());
        }

        @Override
        public void close() {
            for (Map.Entry<String, BuildIndexCache.MetaFile> entry : myJarMetaData.entrySet()) {
                String jarEntryPath = entry.getKey();
                String prevJarName = myMetaSource.put(jarEntryPath, jarName);
                if (prevJarName != null) {
                    throw new IllegalStateException(
                        "Duplicate plugin meta-data " + jarEntryPath + " in both " + prevJarName + " and " + jarName
                    );
                }
                myMetaData.put(jarEntryPath, entry.getValue());
            }
        }
    }

    private static final Set<String> META_FILES = Set.of(
        "META-INF/pluginIcon.svg",
        "META-INF/pluginIcon_dark.svg",
        "META-INF/plugin.xml"
    );

    private Map<String, BuildIndexCache.MetaFile> myMetaData = new ConcurrentHashMap<>();
    private Map<String, String> myMetaSource = new ConcurrentHashMap<>();

    @Override
    public Session newSession(String jarName) {
        return new Session(jarName);
    }

    @Override
    public void write(BiConsumer<String, byte[]> consumer) throws IOException {
        for (Map.Entry<String, BuildIndexCache.MetaFile> entry : new TreeMap<>(myMetaData).entrySet()) {
            consumer.accept(entry.getKey(), entry.getValue().getContent().toByteArray());
        }
    }
}
