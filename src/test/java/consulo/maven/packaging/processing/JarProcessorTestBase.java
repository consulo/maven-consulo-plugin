package consulo.maven.packaging.processing;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * @author UNV
 * @since 2026-05-05
 */
public class JarProcessorTestBase {
    record Entry(String path, byte[] bytes) {
        void visitBy(JarProcessorSession session) {
            session.visit(path(), this::bytes);
        }

        static Entry of(String path) {
            return of(path, new byte[0]);
        }

        static Entry of(String path, byte[] bytes) {
            return new Entry(path, bytes);
        }

        static Entry of(String path, String chars) {
            return new Entry(path, chars.getBytes(StandardCharsets.ISO_8859_1));
        }

        static List<Entry> writtenBy(JarProcessor<?> processor) throws IOException {
            List<Entry> entries = new ArrayList<>();
            processor.write((path, bytes) -> entries.add(new Entry(path, bytes)));
            return entries;
        }
    }
}
