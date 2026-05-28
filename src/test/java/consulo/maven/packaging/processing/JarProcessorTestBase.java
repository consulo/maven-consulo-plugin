package consulo.maven.packaging.processing;

import consulo.maven.jar.JarEntrySupplier;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * @author UNV
 * @since 2026-05-05
 */
public class JarProcessorTestBase {
    record Entry(String path, boolean isDirectory, byte[] bytes) implements JarEntrySupplier {
        @Override
        public String getEntryPath() {
            return path();
        }

        @Override
        public byte[] get() {
            return bytes();
        }

        public String getString() {
            return new String(bytes(), StandardCharsets.ISO_8859_1);
        }

        @Override
        @SuppressWarnings("SimplifiableIfStatement")
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            return o instanceof Entry that
                && path.equals(that.path)
                && Arrays.equals(bytes, that.bytes);
        }

        @Override
        public int hashCode() {
            return path.hashCode() * 31 + Arrays.hashCode(bytes);
        }

        static Entry dir(String path) {
            return new Entry(path, true, new byte[0]);
        }

        static Entry of(String path) {
            return of(path, new byte[0]);
        }

        static Entry of(String path, byte[] bytes) {
            return new Entry(path, false, bytes);
        }

        static Entry of(String path, String chars) {
            return new Entry(path, false, chars.getBytes(StandardCharsets.ISO_8859_1));
        }

        static List<Entry> writtenBy(JarProcessor processor) throws IOException {
            List<Entry> entries = new ArrayList<>();
            processor.write((path, bytes) -> entries.add(Entry.of(path, bytes)));
            return entries;
        }
    }
}
