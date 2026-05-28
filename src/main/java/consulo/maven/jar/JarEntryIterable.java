package consulo.maven.jar;

import java.io.Closeable;

/**
 * @author UNV
 * @since 2026-05-27
 */
public interface JarEntryIterable extends Iterable<JarEntrySupplier>, Closeable {
}
