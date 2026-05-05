package consulo.maven.packaging.processing;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author UNV
 * @since 2026-05-05
 */
public class JarIndexProcessorTest extends JarProcessorTestBase {
    JarIndexProcessor myProcessor = new JarIndexProcessor();
    JarIndexProcessor.Session mySession = myProcessor.newSession("Test.jar");

    @Test
    void visitJar() throws IOException {
        Entry.of("Foo.class").visitBy(mySession);
        mySession.close();

        List<Entry> results = Entry.writtenBy(myProcessor);

        assertThat(results)
            .hasSize(2)
            .extracting(Entry::path)
            .containsExactlyInAnyOrder("index.txt", "lib/index.txt");

        String text1 = new String(results.get(0).bytes(), StandardCharsets.ISO_8859_1);
        String text2 = new String(results.get(1).bytes(), StandardCharsets.ISO_8859_1);

        assertThat(text1)
            .isEqualTo(text2)
            .isEqualTo("#Test.jar\nFoo.class\n");
    }

    @Test
    void visitEmptyJar() throws IOException {
        mySession.close();

        assertThat(Entry.writtenBy(myProcessor)).isEmpty();
    }
}
