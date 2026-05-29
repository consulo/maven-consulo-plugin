package consulo.maven.jar.impl;

import consulo.maven.jar.JarEntryIterable;
import consulo.maven.jar.JarEntrySupplier;
import org.apache.maven.shared.utils.io.IOUtil;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Iterator;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * @author UNV
 * @since 2026-05-27
 */
public class JarEntryIterableImpl implements JarEntryIterable {
    public class JarEntrySupplierImpl implements JarEntrySupplier {
        private final JarEntry myJarEntry;
        private byte[] myData = null;

        public JarEntrySupplierImpl(JarEntry jarEntry) {
            myJarEntry = jarEntry;
        }

        @Override
        public String getEntryPath() {
            return myJarEntry.getName();
        }

        @Override
        public boolean isDirectory() {
            return myJarEntry.isDirectory();
        }

        @Override
        public byte[] get() {
            if (myData != null) {
                return myData;
            }
            try (InputStream stream = myJarFile.getInputStream(myJarEntry)) {
                long size = myJarEntry.getSize();
                myData = 0 <= size && size < Integer.MAX_VALUE ? toByteArrayOfSize(stream, (int) size) : IOUtil.toByteArray(stream);
            }
            catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            return myData;
        }

        private static byte[] toByteArrayOfSize(InputStream input, int size) throws IOException {
            byte[] buffer = new byte[size];
            int n = input.readNBytes(buffer, 0, size);
            if (n < size) {
                throw new IllegalStateException("JarEntry has reported size " + size + " and actual size " + n);
            }
            return buffer;
        }
    }

    private final JarFile myJarFile;

    public JarEntryIterableImpl(JarFile jarFile) {
        myJarFile = jarFile;
    }

    @Override
    public Iterator<JarEntrySupplier> iterator() {
        return myJarFile.stream().<JarEntrySupplier>map(JarEntrySupplierImpl::new).iterator();
    }

    @Override
    public void close() throws IOException {
        myJarFile.close();
    }
}
