package consulo.maven.packaging.processing;

import consulo.maven.protobuf.BuildIndexCache;

import java.util.function.Supplier;

/**
 * <p>Index building session for a specific JAR file.</p>
 *
 * <p>There're 2 scenarios:
 * <ol>
 * <li>Parsing JAR file via sequential calls of {@link #visit(String, Supplier)}.</li>
 * <li>Loading cached result of JAR parsing via {@link #loadFrom(BuildIndexCache.JarIndex)}.</li>
 * </ol>
 * </p>
 *
 * <p>Then JAR parsing result will be cached by calling {@link #storeTo(BuildIndexCache.JarIndex.Builder)}.</p>
 *
 * <p>After this {@link #close()} will be called to store JAR parsing result into parent {@code JarProcessor}'s state.</p>
 *
 * @author VISTALL
 * @author UNV
 * @since 2026-01-17
 */
public interface JarProcessorSession {
    /**
     * Visiting file entry in a JAR. Called only sequentially in a specific thread.
     *
     * @param jarEntryPath   Path to a file entry inside JAR.
     * @param dataRequestor  File entry content getter.
     */
    void visit(String jarEntryPath, Supplier<byte[]> dataRequestor);

    /**
     * Loading cached result of JAR parsing.
     *
     * @param jarIndex The cache for specific JAR.
     */
    void loadFrom(BuildIndexCache.JarIndex jarIndex);

    /**
     * Storing result of JAR parsing into a cache for a JAR.
     *
     * @param jarIndexBuilder Builder of the cache for specific JAR.
     */
    void storeTo(BuildIndexCache.JarIndex.Builder jarIndexBuilder);

    /**
     * Adding JAR parsing result to a parent {@code JarProcessor}'s state.
     */
    void close();
}
