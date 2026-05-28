package consulo.maven.packaging.processing;

import consulo.maven.jar.JarEntrySupplier;
import consulo.maven.protobuf.BuildIndexCache;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;

/**
 * @author VISTALL
 * @author UNV
 * @since 2026-01-17
 */
public class JarIndexProcessor implements JarProcessor {
    record Session(String jarName, List<String> paths, Map<String, List<String>> map) implements JarProcessorSession {
        @Override
        public void visit(JarEntrySupplier jarEntrySupplier) {
            paths().add(jarEntrySupplier.getEntryPath());
        }

        @Override
        public void loadFrom(BuildIndexCache.JarCache jarCache) {
            paths.addAll(jarCache.getPathsList());
        }

        @Override
        public void storeTo(BuildIndexCache.JarCache.Builder jarCacheBuilder) {
            jarCacheBuilder.addAllPaths(paths());
        }

        @Override
        public void close() {
            if (!paths().isEmpty()) {
                map().put(jarName(), paths());
            }
        }
    }

    private Map<String, List<String>> myPaths = new ConcurrentHashMap<>();

    @Override
    public Session newSession(String jarName) {
        return new Session(jarName, new ArrayList<>(), myPaths);
    }

    @Override
    public void write(BiConsumer<String, byte[]> consumer) throws IOException {
        if (myPaths.isEmpty()) {
            return;
        }

        writeTextFile(consumer);
    }

    private void writeTextFile(BiConsumer<String, byte[]> consumer) {
        StringBuilder builder = new StringBuilder();
        for (Map.Entry<String, List<String>> entry : new TreeMap<>(myPaths).entrySet()) {
            builder.append('#').append(entry.getKey()).append('\n');
            for (String path : entry.getValue()) {
                builder.append(path).append('\n');
            }
        }

        byte[] bytes = builder.toString().getBytes(StandardCharsets.UTF_8);
        consumer.accept("lib/index.txt", bytes);
        consumer.accept("index.txt", bytes);
    }
}
