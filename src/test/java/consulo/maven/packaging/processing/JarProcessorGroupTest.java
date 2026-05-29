package consulo.maven.packaging.processing;

import consulo.maven.jar.JarEntryIterable;
import consulo.maven.jar.JarEntrySupplier;
import consulo.maven.jar.JarSupplier;
import consulo.maven.protobuf.BuildIndexCache;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.function.BiConsumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * @author UNV
 * @since 2026-05-28
 */
public class JarProcessorGroupTest extends JarProcessorTestBase {
    static final String TEST_JAR_NAME = "Test.jar";
    static final Entry FOO_DIR_ENTRY = Entry.dir("foo");
    static final Entry BAR_CLASS_ENTRY = Entry.of("Bar.class");
    static final long TEST_JAR_LAST_MODIFIED = 123L;

    static final BuildIndexCache.JarCache TEST_JAR_CACHE = BuildIndexCache.JarCache.newBuilder()
        .setPath(TEST_JAR_NAME)
        .setLastModified(TEST_JAR_LAST_MODIFIED)
        .build();
    public static final BuildIndexCache.BuildIndex EMPTY_CACHE = BuildIndexCache.BuildIndex.newBuilder()
        .setVersion(1)
        .build();
    public static final BuildIndexCache.BuildIndex VALID_CACHE = BuildIndexCache.BuildIndex.newBuilder(EMPTY_CACHE)
        .addJars(TEST_JAR_CACHE)
        .build();

    final JarEntryIterable myJarEntryIterable = mock(JarEntryIterable.class);
    final JarSupplier myJarSupplier = mock(JarSupplier.class);
    final JarProcessorSession mySession = mock(JarProcessorSession.class);
    final JarProcessor myProcessor = mock(JarProcessor.class);
    @SuppressWarnings("unchecked")
    final BiConsumer<String, byte[]> myIndexFileConsumer = mock(BiConsumer.class);
    final JarProcessorGroup myProcessorGroup = new JarProcessorGroup(myProcessor);

    public JarProcessorGroupTest() throws IOException {
        when(myJarEntryIterable.iterator()).thenReturn(List.<JarEntrySupplier>of(FOO_DIR_ENTRY, BAR_CLASS_ENTRY).iterator());
        when(myJarSupplier.getCanonicalPath()).thenReturn(TEST_JAR_NAME);
        when(myJarSupplier.getLastModifiedTime()).thenReturn(TEST_JAR_LAST_MODIFIED);
        when(myJarSupplier.get()).thenReturn(myJarEntryIterable);
        when(myProcessor.newSession(any())).thenReturn(mySession);
    }

    @Test
    void noCache() throws IOException {
        assertThat(cacheWrittenBy(myProcessorGroup)).isEqualTo(EMPTY_CACHE);
    }

    @Test
    void emptyCache() throws IOException {
        myProcessorGroup.readCache(EMPTY_CACHE::toByteArray);
        assertThat(cacheWrittenBy(myProcessorGroup)).isEqualTo(EMPTY_CACHE);
    }

    @Test
    void correctCache() throws IOException {
        myProcessorGroup.readCache(VALID_CACHE::toByteArray);
        assertThat(cacheWrittenBy(myProcessorGroup)).isEqualTo(VALID_CACHE);
    }

    @Test
    void invalidCacheVersion() throws IOException {
        BuildIndexCache.BuildIndex wrongCache = BuildIndexCache.BuildIndex.newBuilder(VALID_CACHE).setVersion(0).build();
        myProcessorGroup.readCache(wrongCache::toByteArray);
        assertThat(cacheWrittenBy(myProcessorGroup)).isEqualTo(EMPTY_CACHE);
    }

    @Test
    void corruptedCache() throws IOException {
        myProcessorGroup.readCache(() -> new byte[]{-1});
        assertThat(cacheWrittenBy(myProcessorGroup)).isEqualTo(EMPTY_CACHE);
    }

    @Test
    void writeIndexFiles() throws IOException {
        myProcessorGroup.writeIndexFiles(myIndexFileConsumer);

        verify(myProcessor, times(1)).write(myIndexFileConsumer);
    }

    @Test
    void noCacheParsing() throws IOException {
        myProcessorGroup.readFromJar(myJarSupplier);

        verify(mySession, times(1)).visit(BAR_CLASS_ENTRY);
        verify(mySession, never()).loadFrom(any());
        verify(mySession, times(1)).storeTo(any());
        verify(mySession, times(1)).close();

        assertThat(cacheWrittenBy(myProcessorGroup)).isEqualTo(VALID_CACHE);
    }

    @Test
    void validCacheParsing() throws IOException {
        myProcessorGroup.readCache(VALID_CACHE::toByteArray);

        myProcessorGroup.readFromJar(myJarSupplier);

        verify(mySession, never()).visit(BAR_CLASS_ENTRY);
        verify(mySession, times(1)).loadFrom(TEST_JAR_CACHE);
        verify(mySession, never()).storeTo(any());
        verify(mySession, times(1)).close();

        assertThat(cacheWrittenBy(myProcessorGroup)).isEqualTo(VALID_CACHE);
    }

    @Test
    void outdatedCacheParsing() throws IOException {
        BuildIndexCache.JarCache outdatedJarCache = BuildIndexCache.JarCache.newBuilder(TEST_JAR_CACHE)
            .setLastModified(TEST_JAR_LAST_MODIFIED - 1)
            .build();
        BuildIndexCache.BuildIndex outdatedCache = BuildIndexCache.BuildIndex.newBuilder(EMPTY_CACHE).addJars(outdatedJarCache).build();

        myProcessorGroup.readCache(outdatedCache::toByteArray);

        myProcessorGroup.readFromJar(myJarSupplier);

        verify(mySession, times(1)).visit(BAR_CLASS_ENTRY);
        verify(mySession, never()).loadFrom(any());
        verify(mySession, times(1)).storeTo(any());
        verify(mySession, times(1)).close();

        assertThat(cacheWrittenBy(myProcessorGroup)).isEqualTo(VALID_CACHE);
    }

    BuildIndexCache.BuildIndex cacheWrittenBy(JarProcessorGroup processorGroup) throws IOException {
        ByteArrayOutputStream s = new ByteArrayOutputStream();
        processorGroup.writeCache(bytes -> {
            try {
                s.write(bytes);
            }
            catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });
        return BuildIndexCache.BuildIndex.parseFrom(s.toByteArray());
    }
}
