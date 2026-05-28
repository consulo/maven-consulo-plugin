package consulo.maven.jar;

import java.util.function.Supplier;

/**
 * @author UNV
 * @since 2026-05-27
 */
public interface JarEntrySupplier extends Supplier<byte[]> {
    String getEntryPath();

    boolean isDirectory();
}
