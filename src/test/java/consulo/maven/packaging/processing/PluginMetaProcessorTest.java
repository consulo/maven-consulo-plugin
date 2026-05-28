package consulo.maven.packaging.processing;

import com.google.protobuf.ByteString;
import consulo.maven.protobuf.BuildIndexCache;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * @author UNV
 * @since 2026-05-28
 */
public class PluginMetaProcessorTest extends JarProcessorTestBase {
    static final Entry PLUGIN_ICON_LIGHT_ENTRY = Entry.of("META-INF/pluginIcon.svg", "<svg/> <!-- light -->");
    static final Entry PLUGIN_ICON_DARK_ENTRY = Entry.of("META-INF/pluginIcon_dark.svg", "<svg/> <!-- dark -->");
    static final Entry PLUGIN_XML_ENTRY = Entry.of("META-INF/plugin.xml", "<xml/>");

    PluginMetaProcessor myProcessor = new PluginMetaProcessor();
    PluginMetaProcessor.Session mySession = myProcessor.newSession("Test.jar");

    @Test
    void visitNonMetaFile() throws IOException {
        mySession.visit(Entry.of("Foo.class"));
        mySession.close();
        assertThat(Entry.writtenBy(myProcessor)).isEmpty();
    }

    @Test
    void visitMetaFiles() throws IOException {
        mySession.visit(PLUGIN_ICON_LIGHT_ENTRY);
        mySession.visit(PLUGIN_ICON_DARK_ENTRY);
        mySession.visit(PLUGIN_XML_ENTRY);
        mySession.close();

        assertThat(Entry.writtenBy(myProcessor))
            .hasSize(3)
            .containsExactly(PLUGIN_XML_ENTRY, PLUGIN_ICON_LIGHT_ENTRY, PLUGIN_ICON_DARK_ENTRY);
    }

    @Test
    void cacheMetaFiles() throws IOException {
        BuildIndexCache.JarCache jarCache = BuildIndexCache.JarCache.newBuilder()
            .addMetaData(toMetaFile(PLUGIN_XML_ENTRY))
            .addMetaData(toMetaFile(PLUGIN_ICON_LIGHT_ENTRY))
            .addMetaData(toMetaFile(PLUGIN_ICON_DARK_ENTRY))
            .build();

        mySession.loadFrom(jarCache);

        BuildIndexCache.JarCache.Builder storedJarCache = BuildIndexCache.JarCache.newBuilder();
        mySession.storeTo(storedJarCache);
        assertThat(storedJarCache.build()).isEqualTo(jarCache);

        mySession.close();

        assertThat(Entry.writtenBy(myProcessor))
            .hasSize(3)
            .containsExactly(PLUGIN_XML_ENTRY, PLUGIN_ICON_LIGHT_ENTRY, PLUGIN_ICON_DARK_ENTRY);
    }

    @Test
    void visitDuplicateMetaFiles() throws IOException {
        mySession.visit(PLUGIN_XML_ENTRY);
        mySession.close();

        PluginMetaProcessor.Session otherSession = myProcessor.newSession("Test2.jar");
        otherSession.visit(PLUGIN_XML_ENTRY);

        assertThatThrownBy(otherSession::close)
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("Duplicate plugin meta-data " + PLUGIN_XML_ENTRY.path() + " in both Test.jar and Test2.jar");
    }

    BuildIndexCache.MetaFile toMetaFile(Entry entry) {
        return BuildIndexCache.MetaFile.newBuilder()
            .setPath(entry.path())
            .setContent(ByteString.copyFrom(entry.get())).build();
    }
}
