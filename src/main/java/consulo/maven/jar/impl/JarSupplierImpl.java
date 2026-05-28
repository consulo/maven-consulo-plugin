package consulo.maven.jar.impl;

import consulo.maven.jar.JarEntryIterable;
import consulo.maven.jar.JarSupplier;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.jar.JarFile;

/**
 * @author UNV
 * @since 2026-05-27
 */
public class JarSupplierImpl implements JarSupplier {
    private final File myJarFile;

    public JarSupplierImpl(File jarFile) {
        myJarFile = jarFile;
    }

    @Override
    public String getName() {
        return myJarFile.getName();
    }

    @Override
    public String getCanonicalPath() throws IOException {
        return myJarFile.getCanonicalPath();
    }

    @Override
    public long getLastModifiedTime() {
        return myJarFile.lastModified();
    }

    @Override
    public JarEntryIterable get() {
        try {
            return new JarEntryIterableImpl(new JarFile(myJarFile));
        }
        catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}

