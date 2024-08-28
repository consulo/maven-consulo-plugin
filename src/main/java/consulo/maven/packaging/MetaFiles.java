package consulo.maven.packaging;

import org.apache.maven.shared.utils.io.IOUtil;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
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
    private List<String> myIndexData = new ArrayList<>();

    public MetaFiles() {
    }

    public void readFromJar(File jarFile) throws IOException {
        myIndexData.add("#" + jarFile.getName());

        try (JarFile jar = new JarFile(jarFile)) {
            Enumeration<JarEntry> entries = jar.entries();
            while (entries.hasMoreElements()) {
                JarEntry jarEntry = entries.nextElement();
                String jarName = jarEntry.getName();

                if (META_FILES.contains(jarName)) {
                    try (InputStream stream = jar.getInputStream(jarEntry)) {
                        myMetaData.put(jarName, IOUtil.toString(stream));
                    }
                }

                if (!jarEntry.isDirectory()) {
                    myIndexData.add(jarName);
                }
            }
        }
    }

    public void forEachData(BiConsumer<String, String> consumer) {
        if (!myIndexData.isEmpty())  {
            consumer.accept("lib/index.txt", String.join("\n", myIndexData));
        }

        for (Map.Entry<String, String> entry : myMetaData.entrySet()) {
            consumer.accept(entry.getKey(), entry.getValue());
        }
    }
}
