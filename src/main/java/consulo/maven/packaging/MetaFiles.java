package consulo.maven.packaging;

import consulo.maven.packaging.processing.*;
import org.apache.maven.shared.utils.io.IOUtil;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.Supplier;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * @author VISTALL
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

    public static final Set<String> META_FILES = Set.of(
        "META-INF/pluginIcon.svg",
        "META-INF/pluginIcon_dark.svg",
        "META-INF/plugin.xml"
    );

    private Map<String, String> myMetaData = new ConcurrentHashMap<>();

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

        for (Map.Entry<String, String> entry : new TreeMap<>(myMetaData).entrySet()) {
            consumer.accept(entry.getKey(), entry.getValue().getBytes(StandardCharsets.UTF_8));
        }
    }
}
