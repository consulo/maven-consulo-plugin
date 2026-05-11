package consulo.maven.packaging;

import consulo.maven.packaging.processing.*;
import consulo.maven.protobuf.BuildIndexCache;
import org.apache.maven.shared.utils.io.IOUtil;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * @author VISTALL
 * @author UNV
 * @since 2023-01-26
 */
public class MetaFiles {
    private static class JarDataRequestor implements Supplier<byte[]> {
        private final JarFile myJar;
        private final JarEntry myJarEntry;
        private byte[] myData = null;

        private JarDataRequestor(JarFile jar, JarEntry jarEntry) {
            myJar = jar;
            myJarEntry = jarEntry;
        }

        @Override
        public byte[] get() {
            if (myData != null) {
                return myData;
            }
            try (InputStream stream = myJar.getInputStream(myJarEntry)) {
                long size = myJarEntry.getSize();
                myData = 0 <= size && size < Integer.MAX_VALUE ? toByteArrayOfSize(stream, (int) size) : IOUtil.toByteArray(stream);
            }
            catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            return myData;
        }

        private static byte[] toByteArrayOfSize(InputStream input, int size) throws IOException {
            byte[] buffer = new byte[size];
            int n = input.readNBytes(buffer, 0, size);
            if (n < size) {
                throw new IllegalStateException("JarEntry has reported size " + size + " and actual size " + n);
            }
            return buffer;
        }
    }

    public static final int CACHE_VERSION = 1;

    public static final Set<String> META_FILES = Set.of(
        "META-INF/pluginIcon.svg",
        "META-INF/pluginIcon_dark.svg",
        "META-INF/plugin.xml"
    );

    private Map<String, String> myMetaData = new ConcurrentHashMap<>();
    private Map<String, BuildIndexCache.JarIndex> myCache = new ConcurrentHashMap<>();

    private final List<JarProcessor> myJarProcessors = List.of(
        new JarIndexProcessor(),
        new IconJarProcessor(),
        new LocalizeJarProcessor()
    );

    public void readFromJar(File jarFile) throws IOException {
        List<JarProcessorSession> sessions = new ArrayList<>(myJarProcessors.size());
        for (JarProcessor jarProcessor : myJarProcessors) {
            sessions.add(jarProcessor.newSession(jarFile.getName()));
        }

        String jarFileCanonicalPath = jarFile.getCanonicalPath();
        BuildIndexCache.JarIndex jarIndex = myCache.get(jarFileCanonicalPath);
        long lastModified = jarFile.lastModified();
        if (jarIndex == null || lastModified > jarIndex.getLastModified()) {
            parseJar(jarFile, sessions);

            BuildIndexCache.JarIndex.Builder jarIndexBuilder = BuildIndexCache.JarIndex.newBuilder()
                .setLastModified(lastModified);

            for (JarProcessorSession session : sessions) {
                session.storeTo(jarIndexBuilder);
            }

            myCache.put(jarFileCanonicalPath, jarIndexBuilder.build());
        }
        else {
            for (JarProcessorSession session : sessions) {
                session.loadFrom(jarIndex);
            }
        }

        for (JarProcessorSession session : sessions) {
            session.close();
        }
    }

    private void parseJar(File jarFile, List<JarProcessorSession> sessions) throws IOException {
        try (JarFile jar = new JarFile(jarFile)) {
            for (Enumeration<JarEntry> entries = jar.entries(); entries.hasMoreElements(); ) {
                JarEntry jarEntry = entries.nextElement();
                if (jarEntry.isDirectory()) {
                    continue;
                }

                String jarEntryPath = jarEntry.getName();
                JarDataRequestor dataRequestor = new JarDataRequestor(jar, jarEntry);

                if (META_FILES.contains(jarEntryPath)) {
                    myMetaData.put(jarEntryPath, new String(dataRequestor.get(), StandardCharsets.UTF_8));
                }

                for (JarProcessorSession session : sessions) {
                    session.visit(jarEntryPath, dataRequestor);
                }
            }
        }
    }

    public void writeIndexFiles(BiConsumer<String, byte[]> consumer) throws IOException {
        for (JarProcessor jarProcessor : myJarProcessors) {
            jarProcessor.write(consumer);
        }
    }

    public void forEachData(BiConsumer<String, byte[]> consumer) throws IOException {
        writeIndexFiles(consumer);

        for (Map.Entry<String, String> entry : new TreeMap<>(myMetaData).entrySet()) {
            consumer.accept(entry.getKey(), entry.getValue().getBytes(StandardCharsets.UTF_8));
        }
    }

    public void readCache(Supplier<byte[]> cacheSupplier) throws IOException {
        BuildIndexCache.BuildIndex buildCache = BuildIndexCache.BuildIndex.parseFrom(cacheSupplier.get());
        if (buildCache.getVersion() != CACHE_VERSION) {
            return;
        }

        for (int i = buildCache.getJarsCount(); --i >= 0; ) {
            BuildIndexCache.JarCache jarCache = buildCache.getJars(i);
            myCache.put(jarCache.getPath(), jarCache.getJarIndex());
        }
    }

    protected void writeCache(Consumer<byte[]> cacheConsumer) throws IOException {
        BuildIndexCache.BuildIndex.Builder builder = BuildIndexCache.BuildIndex.newBuilder()
            .setVersion(CACHE_VERSION);
        for (Map.Entry<String, BuildIndexCache.JarIndex> cacheEntry : new TreeMap<>(myCache).entrySet()) {
            BuildIndexCache.JarCache jarCache = BuildIndexCache.JarCache.newBuilder()
                .setPath(cacheEntry.getKey())
                .setJarIndex(cacheEntry.getValue())
                .build();
            builder.addJars(jarCache);
        }

        cacheConsumer.accept(builder.build().toByteArray());
    }
}
