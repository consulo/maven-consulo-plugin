package consulo.maven.packaging.processing;

import java.util.function.Supplier;

/**
 * @author VISTALL
 * @since 2026-01-17
 */
public interface JarProcessorSession {
    void visit(String jarEntryPath, Supplier<byte[]> dataRequestor);

    void close();
}
