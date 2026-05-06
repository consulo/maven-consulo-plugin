package consulo.maven.packaging.processing;

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
        Entry.of(FOO_LOC_YAML).visitBy(mySession);
        Entry.of(LOCALIZATION_ROOT + '/' + FOO_LOC_YAML).visitBy(mySession);
        Entry.of(PATH_PREFIX + FOO_LOC_ID + ".json").visitBy(mySession);
        mySession.close();
        assertThat(Entry.writtenBy(myProcessor)).isEmpty();
    }

    @Test
    void visitYaml() throws IOException {
        LocalizeProto.LocalizeIndex.Builder indexBuilder = LocalizeProto.LocalizeIndex.newBuilder()
            .setVersion(1)
            .addLocalizes(
                LocalizeProto.Localize.newBuilder()
                    .setId(FOO_LOC_ID)
                    .setLocale(LOCALE)
                    .addTexts(LocalizeProto.Text.newBuilder().setId("foo.bar").setText("Foobar"))
            );

        FOO_LOC_ENTRY.visitBy(mySession);
        mySession.close();

        List<Entry> results = Entry.writtenBy(myProcessor);

        assertThat(results)
            .hasSize(1)
            .extracting(Entry::path)
            .containsExactly("localize-index.bin");

        assertThat(LocalizeProto.LocalizeIndex.parseFrom(results.get(0).bytes()))
            .isEqualTo(indexBuilder.build());
    }

    @Test
    void visitDuplicateYaml() throws IOException {
        FOO_LOC_ENTRY.visitBy(mySession);
        FOO_LOC_ENTRY.visitBy(mySession);
        assertThatThrownBy(() -> mySession.close())
            .isInstanceOf(IllegalStateException.class)
            .hasMessage(
                "Duplicate main YAML for " + LOCALE + '/' + FOO_LOC_ID + ": " +
                    FOO_LOC_ENTRY.path() + " and " + FOO_LOC_ENTRY.path()
            );
    }

    @Test
    void visitDuplicateAcrossJarsYaml() throws IOException {
        FOO_LOC_ENTRY.visitBy(mySession);
        mySession.close();

        LocalizeJarProcessor.Session session2 = myProcessor.newSession("Test2.jar");
        FOO_LOC_ENTRY.visitBy(session2);
        assertThatThrownBy(session2::close)
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("Duplicate localization across jars: locale=" + LOCALE + ", id=" + FOO_LOC_ID);
    }

    @Test
    void visitEmptyYaml() throws IOException {
        LocalizeProto.LocalizeIndex.Builder indexBuilder = LocalizeProto.LocalizeIndex.newBuilder()
            .setVersion(1)
            .addLocalizes(
                LocalizeProto.Localize.newBuilder()
                    .setId(FOO_LOC_ID)
                    .setLocale(LOCALE)
            );

        Entry.of(FOO_LOC_ENTRY.path()).visitBy(mySession);
        mySession.close();

        List<Entry> results = Entry.writtenBy(myProcessor);

        assertThat(results)
            .hasSize(1)
            .extracting(Entry::path)
            .containsExactly("localize-index.bin");

        assertThat(LocalizeProto.LocalizeIndex.parseFrom(results.get(0).bytes()))
            .isEqualTo(indexBuilder.build());
    }

    @Test
    void visitInvalidYaml() throws IOException {
        Entry.of(FOO_LOC_ENTRY.path(), "%").visitBy(mySession);
        assertThatThrownBy(() -> mySession.close())
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("Failed to parse: " + FOO_LOC_ENTRY.path());
    }

    @Test
    void visitHtml() throws IOException {
        LocalizeProto.LocalizeIndex.Builder indexBuilder = LocalizeProto.LocalizeIndex.newBuilder()
            .setVersion(1)
            .addLocalizes(
                LocalizeProto.Localize.newBuilder()
                    .setId(FOO_LOC_ID)
                    .setLocale(LOCALE)
                    .addTexts(LocalizeProto.Text.newBuilder().setId("foo.bar").setText(BAR_LOC_HTML_CONTENTS))
            );

        BAR_LOC_ENTRY.visitBy(mySession);
        mySession.close();

        List<Entry> results = Entry.writtenBy(myProcessor);

        assertThat(results)
            .hasSize(1)
            .extracting(Entry::path)
            .containsExactly("localize-index.bin");

        assertThat(LocalizeProto.LocalizeIndex.parseFrom(results.get(0).bytes()))
            .isEqualTo(indexBuilder.build());
    }

    @Test
    void visitDuplicateHtml() throws IOException {
        BAR_LOC_ENTRY.visitBy(mySession);
        BAR_LOC_ENTRY.visitBy(mySession);
        assertThatThrownBy(() -> mySession.close())
            .isInstanceOf(IllegalStateException.class)
            .hasMessage(
                "Duplicate localization key 'foo.bar' for " + LOCALE + '/' + FOO_LOC_ID + " (entry: " + BAR_LOC_ENTRY.path() + ")"
            );
    }
}
