package consulo.maven.packaging.processing;

import consulo.maven.protobuf.JarIndexOuterClass;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

/**
 * @author VISTALL
 * @since 2026-01-17
 */
public class JarIndexProcessor implements JarProcessor<JarIndexProcessor.Session> {
    public record Session(String jarName, List<String> paths, Map<String, List<String>> map) implements JarProcessorSession{
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

        JarIndexOuterClass.JarIndex.Builder builder = JarIndexOuterClass.JarIndex.newBuilder();
        builder.setVersion(1);

        for (Map.Entry<String, List<String>> entry : myPaths.entrySet()) {
            JarIndexOuterClass.JarInfo.Builder jarInfoBuilder = JarIndexOuterClass.JarInfo.newBuilder();
            jarInfoBuilder.setJarName(entry.getKey());
            jarInfoBuilder.addAllPaths(entry.getValue());

            builder.addJars(jarInfoBuilder);
        }

        JarIndexOuterClass.JarIndex jarIndex = builder.build();

        consumer.accept("jar-index.bin", jarIndex.toByteArray());
    }

    private void writeTextFile(BiConsumer<String, byte[]> consumer) {
        StringBuilder builder = new StringBuilder();
        for (Map.Entry<String, List<String>> entry : myPaths.entrySet()) {
            builder.append("#").append(entry.getKey()).append("\n");
            for (String path : entry.getValue()) {
                builder.append(path).append("\n");
            }
        }

        consumer.accept("lib/index.txt", builder.toString().getBytes(StandardCharsets.UTF_8));
    }
}
