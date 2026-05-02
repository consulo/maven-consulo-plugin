package consulo.maven.packaging.processing;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

/**
 * @author VISTALL
 * @since 2026-01-17
 */
public class JarIndexProcessor implements JarProcessor<JarIndexProcessor.Session> {
    public record Session(String jarName, List<String> paths, Map<String, List<String>> map) implements JarProcessorSession {
        @Override
        public void visit(String jarEntryPath, Supplier<byte[]> dataRequestor) {
            paths().add(jarEntryPath);
        }

        @Override
        public void close() {
            if (!paths().isEmpty()) {
                map().put(jarName(), paths());
            }
        }
    }

    private Map<String, List<String>> myPaths = new LinkedHashMap<>();

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
        for (Map.Entry<String, List<String>> entry : myPaths.entrySet()) {
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
