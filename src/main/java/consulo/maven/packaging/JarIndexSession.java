package consulo.maven.packaging;

import consulo.maven.protobuf.JarIndexOuterClass;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

/**
 * @author VISTALL
 * @since 2026-01-17
 */
public class JarIndexSession {
    private Map<String, List<String>> myPaths = new LinkedHashMap<>();

    public void add(String jarName, List<String> paths) {
        myPaths.put(jarName, paths);
    }

    public void write(BiConsumer<String, byte[]> consumer) throws IOException {
        if (myPaths.isEmpty()) {
            return;
        }

        writeTextFile(consumer);

        JarIndexOuterClass.JarIndex.Builder builder = JarIndexOuterClass.JarIndex.newBuilder();
        for (Map.Entry<String, List<String>> entry : myPaths.entrySet()) {
            JarIndexOuterClass.JarInfo.Builder jarInfoBuilder = JarIndexOuterClass.JarInfo.newBuilder();
            jarInfoBuilder.setJarName(entry.getKey());
            jarInfoBuilder.addAllPaths(entry.getValue());

            builder.addJars(jarInfoBuilder);
        }

        JarIndexOuterClass.JarIndex jarIndex = builder.build();

        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        jarIndex.writeTo(stream);

        consumer.accept("jar-index.bin", stream.toByteArray());
    }

    private void writeTextFile(BiConsumer<String, byte[]> consumer) {
        StringBuilder builder = new StringBuilder();
        for (Map.Entry<String, List<String>> entry : myPaths.entrySet()) {
            builder.append(entry.getKey()).append("\n");
            for (String path : entry.getValue()) {
                builder.append(path).append("\n");
            }
        }

        consumer.accept("lib/index.txt", builder.toString().getBytes(StandardCharsets.UTF_8));
    }
}
