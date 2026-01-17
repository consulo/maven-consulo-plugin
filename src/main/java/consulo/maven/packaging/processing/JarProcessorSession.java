package consulo.maven.packaging.processing;

/**
 * @author VISTALL
 * @since 2026-01-17
 */
public interface JarProcessorSession {
    void visit(String jarEntryPath);

    void close();
}
