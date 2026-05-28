package consulo.maven.packaging.processing;

import consulo.maven.protobuf.BuildIndexCache;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author UNV
 * @since 2026-05-05
 */
public class JarIndexProcessorTest extends JarProcessorTestBase {
    static final Entry FOO_CLASS_ENTRY = Entry.of("Foo.class");

    JarIndexProcessor myProcessor = new JarIndexProcessor();
    JarIndexProcessor.Session mySession = myProcessor.newSession("Test.jar");

    @Test
    void visitJar() throws IOException {
        mySession.visit(FOO_CLASS_ENTRY);
        mySession.close();

        List<Entry> results = Entry.writtenBy(myProcessor);

        assertThat(results)
            .hasSize(2)
            .extracting(Entry::path)
            .containsExactlyInAnyOrder("index.txt", "lib/index.txt");

        assertThat(results.get(0).getString())
            .isEqualTo(results.get(1).getString())
            .isEqualTo("#Test.jar\n" + FOO_CLASS_ENTRY.path() + "\n");
    }

    @Test
    void cacheJar() throws IOException {
        BuildIndexCache.JarCache jarCache = BuildIndexCache.JarCache.newBuilder()
            .addPaths(FOO_CLASS_ENTRY.path())
            .build();

        mySession.loadFrom(jarCache);

        BuildIndexCache.JarCache.Builder storedJarCache = BuildIndexCache.JarCache.newBuilder();
        mySession.storeTo(storedJarCache);
        assertThat(storedJarCache.build()).isEqualTo(jarCache);

        mySession.close();

        List<Entry> results = Entry.writtenBy(myProcessor);

        assertThat(results)
            .hasSize(2)
            .extracting(Entry::path)
            .containsExactlyInAnyOrder("index.txt", "lib/index.txt");

        assertThat(results.get(0).getString())
            .isEqualTo(results.get(1).getString())
            .isEqualTo("#Test.jar\n" + FOO_CLASS_ENTRY.path() + "\n");
    }

    @Test
    void visitEmptyJar() throws IOException {
        mySession.close();

        assertThat(Entry.writtenBy(myProcessor)).isEmpty();
    }
}
