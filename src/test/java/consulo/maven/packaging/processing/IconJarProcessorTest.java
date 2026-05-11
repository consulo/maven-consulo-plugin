package consulo.maven.packaging.processing;

import com.google.protobuf.ByteString;
import consulo.maven.packaging.processing.xml.SvgCleanupHandler;
import consulo.maven.protobuf.IconIndex;
import org.junit.jupiter.api.Test;
import org.xml.sax.InputSource;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * @author UNV
 * @since 2026-05-04
 */
public class IconJarProcessorTest extends JarProcessorTestBase {
    static final String ICON_ROOT = "ICON-LIB";
    static final String THEME = "light";
    static final String GROUP_ID = "FooIconGroup";
    static final String PATH_PREFIX = ICON_ROOT + '/' + THEME + '/' + GROUP_ID + '/';

    static final String ZOOM_OUT_SVG = """
        <svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 16 16" fill="none">
        <circle cx="8" cy="8" r="6.5" stroke="#CED0D6"/>
        <rect x="4" y="7.5" width="8" height="1" rx="0.5" fill="#CED0D6"/>
        </svg>
        """;

    @SuppressWarnings("SpellCheckingInspection")
    static final byte[] BLACK_16_PNG = Base64.getDecoder().decode(
        "iVBORw0KGgoAAAANSUhEUgAAABAAAAAQCAYAAAAf8/9hAAAACXBIWXMAAA7EAAAOxAGVKw4bAAAAS0lEQVQ4jWNkYGB4ycDAwMPAwMDEgAn+" +
            "ERD7wQLVzIVFITGABZutJAEmBuxOJ8kAil0w8AZgiyr6umAYGDDEA5GFgYHhB5QmB/wAAIcLCBsQodqvAAAAAElFTkSuQmCC"
    );

    @SuppressWarnings("SpellCheckingInspection")
    static final byte[] BLACK_32_PNG = Base64.getDecoder().decode(
        "iVBORw0KGgoAAAANSUhEUgAAACAAAAAgCAYAAABzenr0AAAACXBIWXMAAA7EAAAOxAGVKw4bAAAAbUlEQVRYhe3VsQ2AMAxE0Y/lIgNQULD/OqyCMgCihCKSG4yRuKu" +
            "iNH6JLsoEbMACOGBcua9HOR7Y6w6swBwMy0qLTpkeI77qdEBpBFAHBBDAGH8WrwJKI4AAegUCfAKgEgpQDvh3CR3oQCuav58qlAw73kKCSgAAAABJRU5ErkJggg=="
    );

    static final Entry ZOOM_OUT_SVG_ENTRY = Entry.of(PATH_PREFIX + "zoomOut.svg", ZOOM_OUT_SVG);
    static final Entry BLACK_16_PNG_ENTRY = Entry.of(PATH_PREFIX + "black.png", BLACK_16_PNG);
    static final Entry BLACK_32_PNG_ENTRY = Entry.of(PATH_PREFIX + "black@2x.png", BLACK_32_PNG);

    IconJarProcessor myProcessor = new IconJarProcessor();
    IconJarProcessor.Session mySession = myProcessor.newSession("Test.jar");

    @Test
    void visitNonIconLib() throws IOException {
        Entry.of("foo.svg").visitBy(mySession);
        mySession.close();
        assertThat(Entry.writtenBy(myProcessor)).isEmpty();
    }

    @Test
    void visitNonIconFile() throws IOException {
        Entry.of(PATH_PREFIX + "foo.bmp").visitBy(mySession);
        mySession.close();
        assertThat(Entry.writtenBy(myProcessor)).isEmpty();
    }

    @Test
    void visitSvg() throws IOException {
        @SuppressWarnings("SpellCheckingInspection")
        IconIndex.IconGroupIndex.Builder indexBuilder = IconIndex.IconGroupIndex.newBuilder()
            .setVersion(1)
            .addIconGroups(
                IconIndex.IconGroup.newBuilder()
                    .setTheme(THEME)
                    .setId(GROUP_ID)
                    .addIcons(
                        IconIndex.Icon.newBuilder()
                            .setId("zoomout")
                            .setX1(
                                IconIndex.IconData.newBuilder()
                                    .setWidth(16)
                                    .setHeight(16)
                                    .setData(ByteString.copyFrom(ZOOM_OUT_SVG.replace("\n", "").getBytes(StandardCharsets.ISO_8859_1)))
                            )
                    )
            );

        ZOOM_OUT_SVG_ENTRY.visitBy(mySession);
        mySession.close();

        List<Entry> results = Entry.writtenBy(myProcessor);
        assertThat(results).hasSize(1);

        assertThat(results.get(0).path()).isEqualTo("icon-index.bin");

        assertThat(IconIndex.IconGroupIndex.parseFrom(results.get(0).bytes()))
            .isEqualTo(indexBuilder.build());
    }

    @Test
    void visitSvgNoDimensions() throws IOException {
        String svgNoDimensions = "<svg viewBox=\"0 0 22 33\"/>";

        @SuppressWarnings("SpellCheckingInspection")
        IconIndex.IconGroupIndex.Builder indexBuilder = IconIndex.IconGroupIndex.newBuilder()
            .setVersion(1)
            .addIconGroups(
                IconIndex.IconGroup.newBuilder()
                    .setTheme(THEME)
                    .setId(GROUP_ID)
                    .addIcons(
                        IconIndex.Icon.newBuilder()
                            .setId("zoomout")
                            .setX1(
                                IconIndex.IconData.newBuilder()
                                    .setWidth(22)
                                    .setHeight(33)
                                    .setData(ByteString.copyFrom(svgNoDimensions.getBytes(StandardCharsets.ISO_8859_1)))
                            )
                    )
            );

        Entry.of(ZOOM_OUT_SVG_ENTRY.path(), svgNoDimensions).visitBy(mySession);
        mySession.close();

        List<Entry> results = Entry.writtenBy(myProcessor);
        assertThat(results).hasSize(1);

        assertThat(results.get(0).path()).isEqualTo("icon-index.bin");

        assertThat(IconIndex.IconGroupIndex.parseFrom(results.get(0).bytes()))
            .isEqualTo(indexBuilder.build());
    }

    @Test
    void visitInvalidSvg() throws IOException {
        assertThatThrownBy(() -> Entry.of(ZOOM_OUT_SVG_ENTRY.path(), BLACK_16_PNG_ENTRY.bytes()).visitBy(mySession))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Failed to clean up: " + ZOOM_OUT_SVG_ENTRY.path());
    }

    @Test
    void visitInvalidSvg2() throws IOException {
        assertThatThrownBy(() -> Entry.of(ZOOM_OUT_SVG_ENTRY.path(), "<foo/>").visitBy(mySession))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Failed to parse SVG width and height: " + ZOOM_OUT_SVG_ENTRY.path());
    }

    @Test
    void visitPng() throws IOException {
        BLACK_16_PNG_ENTRY.visitBy(mySession);
        BLACK_32_PNG_ENTRY.visitBy(mySession);
        mySession.close();

        IconIndex.IconGroupIndex.Builder indexBuilder = IconIndex.IconGroupIndex.newBuilder()
            .setVersion(1)
            .addIconGroups(
                IconIndex.IconGroup.newBuilder()
                    .setTheme(THEME)
                    .setId(GROUP_ID)
                    .addIcons(
                        IconIndex.Icon.newBuilder()
                            .setId("black")
                            .setType(IconIndex.IconType.PNG)
                            .setX1(
                                IconIndex.IconData.newBuilder()
                                    .setHeight(16)
                                    .setWidth(16)
                                    .setData(ByteString.copyFrom(BLACK_16_PNG))
                            )
                            .setX2(
                                IconIndex.IconData.newBuilder()
                                    .setHeight(32)
                                    .setWidth(32)
                                    .setData(ByteString.copyFrom(BLACK_32_PNG))
                            )
                    )
            );

        List<Entry> results = Entry.writtenBy(myProcessor);
        assertThat(results).hasSize(1);

        assertThat(results.get(0).path()).isEqualTo("icon-index.bin");

        assertThat(IconIndex.IconGroupIndex.parseFrom(results.get(0).bytes()))
            .isEqualTo(indexBuilder.build());
    }

    @Test
    void visitDuplicatePng() throws IOException {
        BLACK_16_PNG_ENTRY.visitBy(mySession);
        BLACK_16_PNG_ENTRY.visitBy(mySession);
        assertThatThrownBy(() -> mySession.close())
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("Duplicate icon: " + BLACK_16_PNG_ENTRY.path());
    }

    @Test
    void visitDuplicatePng2x() throws IOException {
        BLACK_32_PNG_ENTRY.visitBy(mySession);
        BLACK_32_PNG_ENTRY.visitBy(mySession);
        assertThatThrownBy(() -> mySession.close())
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("Duplicate @2x icon: " + BLACK_32_PNG_ENTRY.path());
    }

    @Test
    void visitInvalidPng() throws IOException {
        assertThatThrownBy(() -> Entry.of(BLACK_16_PNG_ENTRY.path(), ZOOM_OUT_SVG_ENTRY.bytes()).visitBy(mySession))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Failed to parse: " + BLACK_16_PNG_ENTRY.path());
    }

    @Test
    void visitMissingPng() throws IOException {
        BLACK_32_PNG_ENTRY.visitBy(mySession);
        assertThatThrownBy(() -> mySession.close())
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("Missing x1 icon for " + ICON_ROOT + '/' + THEME + '/' + GROUP_ID + "/black (only @2x found)");
    }

    @Test
    void xmlCleanupTest() throws Exception {
        String xml = "<foo xmlns=\"https://garply.com\">" +
            "<!-- comment -->" +
            " \n\r\t<bar baz=\"\"/> \n\r\ttext \n\r\t<waldo qux=\"quux\"> \n\r\t</waldo> \n\r\t" +
            "</foo>";
        Charset charset = StandardCharsets.ISO_8859_1;
        try (ByteArrayInputStream in = new ByteArrayInputStream(xml.getBytes(charset));
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            mySession.getSaxParser().parse(new InputSource(in), new SvgCleanupHandler(out));

            assertThat(out.toByteArray())
                .asString(charset)
                .isEqualTo("<foo xmlns=\"https://garply.com\"><bar baz=\"\"/>text<waldo qux=\"quux\"/></foo>");
        }
    }
}
