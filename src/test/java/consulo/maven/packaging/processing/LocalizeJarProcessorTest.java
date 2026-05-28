package consulo.maven.packaging.processing;

import consulo.maven.protobuf.BuildIndexCache;
import consulo.maven.protobuf.LocalizeProto;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * @author UNV
 * @since 2026-05-05
 */
public class LocalizeJarProcessorTest extends JarProcessorTestBase {
    static final String LOCALIZATION_ROOT = "LOCALIZE-LIB";
    static final String LOCALE = "en_US";
    static final String PATH_PREFIX = LOCALIZATION_ROOT + "/" + LOCALE + '/';

    static final String FOO_LOC_ID = "FooLocalize";
    static final String FOO_LOC_YAML = FOO_LOC_ID + ".yaml";
    static final String BAR_LOC_HTML = FOO_LOC_ID + "/foo/Bar.html";

    static final String FOO_LOC_YAML_CONTENTS = """
        foo.bar:
            text: Foobar
        """;
    static final String BAR_LOC_HTML_CONTENTS = """
        <html>
        <body>
        <font face="verdana" size="-1">Foobar.</font>
        </body>
        </html>
        """;

    static final Entry FOO_LOC_ENTRY = Entry.of(PATH_PREFIX + FOO_LOC_YAML, FOO_LOC_YAML_CONTENTS);
    static final Entry BAR_LOC_ENTRY = Entry.of(PATH_PREFIX + BAR_LOC_HTML, BAR_LOC_HTML_CONTENTS);

    LocalizeJarProcessor myProcessor = new LocalizeJarProcessor();
    LocalizeJarProcessor.Session mySession = myProcessor.newSession("Test.jar");

    @Test
    void visitNonLocalizationLib() throws IOException {
        mySession.visit(Entry.of(FOO_LOC_YAML));
        mySession.visit(Entry.of(LOCALIZATION_ROOT + '/' + FOO_LOC_YAML));
        mySession.visit(Entry.of(PATH_PREFIX + FOO_LOC_ID + ".json"));
        mySession.close();
        assertThat(Entry.writtenBy(myProcessor)).isEmpty();
    }

    @Test
    void visitYaml() throws IOException {
        LocalizeProto.Localize fooLoc = LocalizeProto.Localize.newBuilder()
            .setId(FOO_LOC_ID)
            .setLocale(LOCALE)
            .addTexts(LocalizeProto.Text.newBuilder().setId("foo.bar").setText("Foobar"))
            .build();

        LocalizeProto.LocalizeIndex localizationIndex = LocalizeProto.LocalizeIndex.newBuilder()
            .setVersion(1)
            .addLocalizes(fooLoc)
            .build();

        mySession.visit(FOO_LOC_ENTRY);
        mySession.close();

        List<Entry> results = Entry.writtenBy(myProcessor);

        assertThat(results)
            .hasSize(1)
            .extracting(Entry::path)
            .containsExactly("localize-index.bin");

        assertThat(LocalizeProto.LocalizeIndex.parseFrom(results.get(0).bytes()))
            .isEqualTo(localizationIndex);
    }

    @Test
    void cacheYaml() throws IOException {
        LocalizeProto.Localize fooLoc = LocalizeProto.Localize.newBuilder()
            .setId(FOO_LOC_ID)
            .setLocale(LOCALE)
            .addTexts(LocalizeProto.Text.newBuilder().setId("foo.bar").setText("Foobar"))
            .build();

        LocalizeProto.LocalizeIndex localizationIndex = LocalizeProto.LocalizeIndex.newBuilder()
            .setVersion(1)
            .addLocalizes(fooLoc)
            .build();

        BuildIndexCache.JarCache jarCache = BuildIndexCache.JarCache.newBuilder()
            .addLocalizations(fooLoc)
            .build();

        mySession.loadFrom(jarCache);

        BuildIndexCache.JarCache.Builder storedJarCache = BuildIndexCache.JarCache.newBuilder();
        mySession.storeTo(storedJarCache);
        assertThat(storedJarCache.build()).isEqualTo(jarCache);

        mySession.close();

        List<Entry> results = Entry.writtenBy(myProcessor);

        assertThat(results)
            .hasSize(1)
            .extracting(Entry::path)
            .containsExactly("localize-index.bin");

        assertThat(LocalizeProto.LocalizeIndex.parseFrom(results.get(0).bytes()))
            .isEqualTo(localizationIndex);
    }

    @Test
    void visitDuplicateYaml() throws IOException {
        mySession.visit(FOO_LOC_ENTRY);
        assertThatThrownBy(() -> mySession.visit(FOO_LOC_ENTRY))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("Duplicate YAML for " + LOCALE + '/' + FOO_LOC_ID + ": " + FOO_LOC_ENTRY.path() + " and " + FOO_LOC_ENTRY.path());
    }

    @Test
    void visitDuplicateAcrossJarsYaml() throws IOException {
        mySession.visit(FOO_LOC_ENTRY);
        mySession.close();

        LocalizeJarProcessor.Session session2 = myProcessor.newSession("Test2.jar");
        session2.visit(FOO_LOC_ENTRY);
        assertThatThrownBy(session2::close)
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("Duplicate localization across jars: LocalizationKey[locale=" + LOCALE + ", id=" + FOO_LOC_ID + "]");
    }

    @Test
    void visitEmptyYaml() throws IOException {
        mySession.visit(Entry.of(FOO_LOC_ENTRY.path()));
        mySession.close();

        List<Entry> results = Entry.writtenBy(myProcessor);

        assertThat(results).isEmpty();
    }

    @Test
    void visitInvalidYaml() throws IOException {
        assertThatThrownBy(() -> mySession.visit(Entry.of(FOO_LOC_ENTRY.path(), "%")))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("Failed to parse: " + FOO_LOC_ENTRY.path());
    }

    @Test
    void visitHtml() throws IOException {
        LocalizeProto.Localize fooLoc = LocalizeProto.Localize.newBuilder()
            .setId(FOO_LOC_ID)
            .setLocale(LOCALE)
            .addTexts(LocalizeProto.Text.newBuilder().setId("foo.bar").setText(BAR_LOC_HTML_CONTENTS))
            .build();

        LocalizeProto.LocalizeIndex localizationIndex = LocalizeProto.LocalizeIndex.newBuilder()
            .setVersion(1)
            .addLocalizes(fooLoc)
            .build();

        mySession.visit(BAR_LOC_ENTRY);
        mySession.close();

        List<Entry> results = Entry.writtenBy(myProcessor);

        assertThat(results)
            .hasSize(1)
            .extracting(Entry::path)
            .containsExactly("localize-index.bin");

        assertThat(LocalizeProto.LocalizeIndex.parseFrom(results.get(0).bytes()))
            .isEqualTo(localizationIndex);
    }

    @Test
    void visitDuplicateHtml() throws IOException {
        mySession.visit(BAR_LOC_ENTRY);
        assertThatThrownBy(() -> mySession.visit(BAR_LOC_ENTRY))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage(
                "Duplicate localization key 'foo.bar' for " + LOCALE + '/' + FOO_LOC_ID + " (entry: " + BAR_LOC_ENTRY.path() + ")"
            );
    }

    @Test
    void visitDuplicateHtmlYaml() throws IOException {
        mySession.visit(BAR_LOC_ENTRY);
        assertThatThrownBy(() -> mySession.visit(FOO_LOC_ENTRY))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage(
                "Duplicate localization key 'foo.bar' for " + LOCALE + '/' + FOO_LOC_ID + " (entry: " + FOO_LOC_ENTRY.path() + ")"
            );
    }
}
