package consulo.maven.packaging.processing;

import java.io.IOException;
import java.util.function.BiConsumer;

/**
 * @author VISTALL
 * @since 2026-01-17
 */
public interface JarProcessor<T extends JarProcessorSession> {
    void write(BiConsumer<String, byte[]> consumer) throws IOException;

    T newSession(String jarName);
}
