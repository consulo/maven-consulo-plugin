package consulo.maven.jar;

import consulo.maven.jar.impl.JarSupplierImpl;

import java.io.File;
import java.io.IOException;
import java.util.function.Supplier;

/**
 * @author UNV
 * @since 2026-05-27
 */
public interface JarSupplier extends Supplier<JarEntryIterable> {
    String getName();

    String getCanonicalPath() throws IOException;

    long getLastModifiedTime();

    public static JarSupplier of(File jarFile) {
        return new JarSupplierImpl(jarFile);
    }
}
