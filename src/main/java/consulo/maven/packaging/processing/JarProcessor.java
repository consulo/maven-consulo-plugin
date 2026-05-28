package consulo.maven.packaging.processing;

import java.io.IOException;
import java.util.function.BiConsumer;

/**
 * @author VISTALL
 * @since 2026-01-17
 */
public interface JarProcessor {
    void write(BiConsumer<String, byte[]> consumer) throws IOException;

    JarProcessorSession newSession(String jarName);
}
