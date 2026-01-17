package consulo.maven.packaging;

import org.apache.maven.shared.utils.io.IOUtil;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * @author VISTALL
 * @since 26/01/2023
 */
public class MetaFiles {
    public static final Set<String> META_FILES = Set.of(
        "META-INF/pluginIcon.svg",
        "META-INF/pluginIcon_dark.svg",
        "META-INF/plugin.xml"
    );

    private Map<String, String> myMetaData = new LinkedHashMap<>();

    private JarIndexSession myJarIndexSession = new JarIndexSession();

    public MetaFiles() {
    }

    public void readFromJar(File jarFile) throws IOException {
        String jarFileName = jarFile.getName();
        List<String> paths = new ArrayList<>();

        try (JarFile jar = new JarFile(jarFile)) {
            Enumeration<JarEntry> entries = jar.entries();
            while (entries.hasMoreElements()) {
                JarEntry jarEntry = entries.nextElement();
                String jarEntryPath = jarEntry.getName();

                if (META_FILES.contains(jarEntryPath)) {
                    try (InputStream stream = jar.getInputStream(jarEntry)) {
                        myMetaData.put(jarEntryPath, IOUtil.toString(stream));
                    }
                }

                if (!jarEntry.isDirectory()) {
                    paths.add(jarEntryPath);
                }
            }
        }

        if (!paths.isEmpty()) {
            myJarIndexSession.add(jarFileName, paths);
        }
    }

    public void writeIndexFiles(BiConsumer<String, byte[]> consumer) throws IOException {
        myJarIndexSession.write(consumer);
    }

    public void forEachData(BiConsumer<String, byte[]> consumer) throws IOException {
        writeIndexFiles(consumer);

        for (Map.Entry<String, String> entry : myMetaData.entrySet()) {
            consumer.accept(entry.getKey(), entry.getValue().getBytes(StandardCharsets.UTF_8));
        }
    }
}
