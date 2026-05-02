package consulo.maven.packaging;

import consulo.maven.packaging.processing.*;
import org.apache.maven.shared.utils.io.IOUtil;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Supplier;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * @author VISTALL
 * @since 2023-01-26
 */
public class MetaFiles {
    public static final Set<String> META_FILES = Set.of(
        "META-INF/pluginIcon.svg",
        "META-INF/pluginIcon_dark.svg",
        "META-INF/plugin.xml"
    );

    private Map<String, String> myMetaData = new LinkedHashMap<>();

    private final List<JarProcessor> myJarProcessors = List.of(
        new JarIndexProcessor(),
        new IconJarProcessor(),
        new LocalizationJarProcessor()
    );

    public void readFromJar(File jarFile) throws IOException {
        List<JarProcessorSession> sessions = new ArrayList<>();
        for (JarProcessor jarProcessor : myJarProcessors) {
            sessions.add(jarProcessor.newSession(jarFile.getName()));
        }

        try (JarFile jar = new JarFile(jarFile)) {
            Enumeration<JarEntry> entries = jar.entries();
            while (entries.hasMoreElements()) {
                JarEntry jarEntry = entries.nextElement();
                String jarEntryPath = jarEntry.getName();

                Supplier<byte[]> dataRequestor = () -> {
                    try (InputStream stream = jar.getInputStream(jarEntry)) {
                        long size = jarEntry.getSize();
                        return size >= 0 ? toByteArrayOfSize(stream, (int) size) : IOUtil.toByteArray(stream);
                    }
                    catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                };

                if (META_FILES.contains(jarEntryPath)) {
                    myMetaData.put(jarEntryPath, new String(dataRequestor.get(), StandardCharsets.UTF_8));
                }

                if (!jarEntry.isDirectory()) {
                    for (JarProcessorSession session : sessions) {
                        session.visit(jarEntryPath, dataRequestor);
                    }
                }
            }
        }

        for (JarProcessorSession session : sessions) {
            session.close();
        }
    }

    public void writeIndexFiles(BiConsumer<String, byte[]> consumer) throws IOException {
        for (JarProcessor jarProcessor : myJarProcessors) {
            jarProcessor.write(consumer);
        }
    }

    public void forEachData(BiConsumer<String, byte[]> consumer) throws IOException {
        writeIndexFiles(consumer);

        for (Map.Entry<String, String> entry : myMetaData.entrySet()) {
            consumer.accept(entry.getKey(), entry.getValue().getBytes(StandardCharsets.UTF_8));
        }
    }

    private static byte[] toByteArrayOfSize(InputStream input, int size) throws IOException {
        byte[] buffer = new byte[size];
        for (int i = 0; i < size; ) {
            int n = input.read(buffer, i, size - i);
            if (n < 0) {
                throw new IllegalStateException("JarEntry has reported size " + size + " and actual size " + i);
            }
            i += n;
        }

        return buffer;
    }
}
