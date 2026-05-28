package consulo.maven.packaging.processing;

import consulo.maven.jar.JarEntrySupplier;
import consulo.maven.protobuf.BuildIndexCache;

/**
 * <p>Index building session for a specific JAR file.</p>
 *
 * <p>There're 2 scenarios:
 * <ol>
 * <li>Parsing JAR file via sequential calls of {@link #visit(JarEntrySupplier)}.</li>
 * <li>Loading cached result of JAR parsing via {@link #loadFrom(BuildIndexCache.JarCache)}.</li>
 * </ol>
 * </p>
 *
 * <p>Then JAR parsing result will be cached by calling {@link #storeTo(BuildIndexCache.JarCache.Builder)}.</p>
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
     * @param jarEntrySupplier JAR file entry.
     */
    void visit(JarEntrySupplier jarEntrySupplier);

    /**
     * Loading cached result of JAR parsing.
     *
     * @param jarCache The cache for specific JAR.
     */
    void loadFrom(BuildIndexCache.JarCache jarCache);

    /**
     * Storing result of JAR parsing into a cache for a JAR.
     *
     * @param jarCacheBuilder Builder of the cache for specific JAR.
     */
    void storeTo(BuildIndexCache.JarCache.Builder jarCacheBuilder);

    /**
     * Adding JAR parsing result to a parent {@code JarProcessor}'s state.
     */
    void close();
}
