package consulo.maven.packaging.processing;

import consulo.maven.jar.JarEntryIterable;
import consulo.maven.jar.JarEntrySupplier;
import consulo.maven.jar.JarSupplier;
import consulo.maven.protobuf.BuildIndexCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * @author UNV
 * @since 2026-05-28
 */
public class JarProcessorGroup {
    private static final Logger LOG = LoggerFactory.getLogger(JarProcessorGroup.class);

    public static final int CACHE_VERSION = 1;

    private Map<String, BuildIndexCache.JarCache> myCache = new ConcurrentHashMap<>();

    private final List<JarProcessor> myJarProcessors;

    public JarProcessorGroup(JarProcessor... jarProcessors) {
        myJarProcessors = List.of(jarProcessors);
    }

    public void readFromJar(JarSupplier jarSupplier) throws IOException {
        List<JarProcessorSession> sessions = new ArrayList<>(myJarProcessors.size());
        for (JarProcessor jarProcessor : myJarProcessors) {
            sessions.add(jarProcessor.newSession(jarSupplier.getName()));
        }

        String jarFileCanonicalPath = jarSupplier.getCanonicalPath();
        BuildIndexCache.JarCache jarCache = myCache.get(jarFileCanonicalPath);
        long lastModified = jarSupplier.getLastModifiedTime();
        if (jarCache == null || lastModified != jarCache.getLastModified()) {
            parseJar(jarSupplier, sessions);

            BuildIndexCache.JarCache.Builder jarCacheBuilder = BuildIndexCache.JarCache.newBuilder()
                .setPath(jarFileCanonicalPath)
                .setLastModified(lastModified);

            for (JarProcessorSession session : sessions) {
                session.storeTo(jarCacheBuilder);
            }

            myCache.put(jarFileCanonicalPath, jarCacheBuilder.build());
        }
        else {
            for (JarProcessorSession session : sessions) {
                session.loadFrom(jarCache);
            }
        }

        for (JarProcessorSession session : sessions) {
            session.close();
        }
    }

    private void parseJar(JarSupplier jarSupplier, List<JarProcessorSession> sessions) throws IOException {
        try (JarEntryIterable jarEntryIterable = jarSupplier.get()) {
            for (JarEntrySupplier jarEntrySupplier : jarEntryIterable) {
                if (jarEntrySupplier.isDirectory()) {
                    continue;
                }

                for (JarProcessorSession session : sessions) {
                    session.visit(jarEntrySupplier);
                }
            }
        }
    }

    public void writeIndexFiles(BiConsumer<String, byte[]> consumer) throws IOException {
        for (JarProcessor jarProcessor : myJarProcessors) {
            jarProcessor.write(consumer);
        }
    }

    public void readCache(Supplier<byte[]> cacheSupplier) throws IOException {
        BuildIndexCache.BuildIndex buildCache;
        try {
            buildCache = BuildIndexCache.BuildIndex.parseFrom(cacheSupplier.get());
        }
        catch (Exception e) {
            LOG.warn("Error loading cache. Proceeding with empty cache", e);
            return;
        }

        if (buildCache.getVersion() != CACHE_VERSION) {
            return;
        }

        for (int i = buildCache.getJarsCount(); --i >= 0; ) {
            BuildIndexCache.JarCache jarCache = buildCache.getJars(i);
            myCache.put(jarCache.getPath(), jarCache);
        }
    }

    public void writeCache(Consumer<byte[]> cacheConsumer) throws IOException {
        BuildIndexCache.BuildIndex cache = BuildIndexCache.BuildIndex.newBuilder()
            .setVersion(CACHE_VERSION)
            .addAllJars(new TreeMap<>(myCache).values())
            .build();

        cacheConsumer.accept(cache.toByteArray());
    }
}
