package consulo.maven.packaging.processing;

import ar.com.hjg.pngj.PngReader;
import com.google.protobuf.ByteString;
import consulo.maven.packaging.processing.xml.SvgCleanupHandler;
import consulo.maven.packaging.processing.xml.SvgDimensionsHandler;
import consulo.maven.packaging.processing.xml.TeeHandler;
import consulo.maven.protobuf.IconIndex;
import org.apache.maven.shared.utils.StringUtils;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import javax.xml.XMLConstants;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

/**
 * @author VISTALL
 * @since 2026-01-17
 */
public class IconJarProcessor implements JarProcessor<IconJarProcessor.Session> {
    public static final String ICON_LIB = "ICON-LIB";

    private record IconGroupAndTheme(String iconGroupId, String themeId) {
    }

    private record IconKey(String themeId, String groupId, String imageId) {
        @Override
        public String toString() {
            return ICON_LIB + '/' + themeId() + '/' + groupId() + '/' + imageId();
        }
    }

    private record RawEntry(
        String jarEntryPath,
        IconKey key,
        boolean is2x,
        IconIndex.IconType type,
        IconIndex.IconData iconData
    ) {
    }

    private static class IconAccumulator {
        IconIndex.IconType type;
        String firstEntryPath;
        IconIndex.IconData x1;
        IconIndex.IconData x2;
    }

    public class Session implements JarProcessorSession {
        private final List<RawEntry> myEntries = new ArrayList<>();

        @Override
        public void visit(String jarEntryPath, Supplier<byte[]> dataRequestor) {
            if (!jarEntryPath.startsWith(ICON_LIB)) {
                return;
            }

            IconIndex.IconType type;
            byte[] data;
            int width;
            int height;

            if (jarEntryPath.endsWith(".svg")) {
                type = IconIndex.IconType.SVG;
                try {
                    byte[] svgData = dataRequestor.get();
                    try (ByteArrayInputStream in = new ByteArrayInputStream(svgData);
                         ByteArrayOutputStream out = new ByteArrayOutputStream(svgData.length)) {

                        SvgDimensionsHandler dimensionsHandler = new SvgDimensionsHandler();

                        getSaxParser().parse(new InputSource(in), new TeeHandler(new SvgCleanupHandler(out), dimensionsHandler));

                        data = out.toByteArray();

                        width = (int) Math.round(dimensionsHandler.getWidth());
                        height = (int) Math.round(dimensionsHandler.getHeight());
                    }
                }
                catch (Exception e) {
                    throw new IllegalArgumentException("Failed to clean up: " + jarEntryPath, e);
                }

                if (width < 0 || height < 0) {
                    throw new IllegalArgumentException("Failed to parse SVG width and height: " + jarEntryPath);
                }
            }
            else if (jarEntryPath.endsWith(".png")) {
                type = IconIndex.IconType.PNG;
                data = dataRequestor.get();

                PngReader reader = null;
                try (InputStream stream = new ByteArrayInputStream(data)) {
                    reader = new PngReader(stream);
                    width = reader.imgInfo.cols;
                    height = reader.imgInfo.rows;
                }
                catch (Exception e) {
                    throw new IllegalArgumentException("Failed to parse: " + jarEntryPath, e);
                }
                finally {
                    if (reader != null) {
                        reader.close();
                    }
                }
            }
            else {
                return;
            }

            IconIndex.IconData iconData = IconIndex.IconData.newBuilder()
                .setHeight(height)
                .setWidth(width)
                .setData(ByteString.copyFrom(data))
                .build();

            String[] split = StringUtils.split(jarEntryPath, "/", 4);

            String imageId = split[3];

            int dotIndex = imageId.lastIndexOf('.');
            imageId = imageId.substring(0, dotIndex);

            boolean is2x = imageId.endsWith("@2x");
            if (is2x) {
                imageId = imageId.substring(0, imageId.length() - 3);
            }

            imageId = imageId.replace('\\', '/').replace('/', '.').replace('-', '_').toLowerCase(Locale.ROOT);

            myEntries.add(new RawEntry(jarEntryPath, new IconKey(split[1], split[2], imageId), is2x, type, iconData));
        }

        @Override
        public void close() {
            Map<IconKey, IconAccumulator> accumulators = new HashMap<>();

            for (RawEntry entry : myEntries) {
                IconAccumulator acc = accumulators.computeIfAbsent(entry.key(), k -> new IconAccumulator());

                if (acc.type == null) {
                    acc.type = entry.type();
                    acc.firstEntryPath = entry.jarEntryPath();
                }
                else if (acc.type != entry.type()) {
                    throw new IllegalStateException(
                        "Icon type mismatch for " + entry.key()
                            + ": " + acc.type + " from " + acc.firstEntryPath
                            + ", " + entry.type() + " from " + entry.jarEntryPath()
                    );
                }

                if (entry.is2x()) {
                    if (acc.x2 != null) {
                        throw new IllegalStateException("Duplicate @2x icon: " + entry.jarEntryPath());
                    }
                    acc.x2 = entry.iconData();
                }
                else {
                    if (acc.x1 != null) {
                        throw new IllegalStateException("Duplicate icon: " + entry.jarEntryPath());
                    }
                    acc.x1 = entry.iconData();
                }
            }

            myEntries.clear();

            for (Map.Entry<IconKey, IconAccumulator> entry : accumulators.entrySet()) {
                IconKey key = entry.getKey();
                IconAccumulator acc = entry.getValue();

                if (acc.x1 == null) {
                    throw new IllegalStateException("Missing x1 icon for " + key + " (only @2x found)");
                }

                IconIndex.Icon.Builder iconBuilder = IconIndex.Icon.newBuilder()
                    .setId(key.imageId())
                    .setType(acc.type)
                    .setX1(acc.x1);
                if (acc.x2 != null) {
                    iconBuilder.setX2(acc.x2);
                }

                myIcons.computeIfAbsent(
                        new IconGroupAndTheme(key.groupId(), key.themeId()),
                        t -> Collections.synchronizedList(new ArrayList<>())
                    )
                    .add(iconBuilder.build());
            }
        }

        private SAXParser saxParser = null;

        SAXParser getSaxParser() throws ParserConfigurationException, SAXException {
            SAXParser parser = saxParser;
            if (parser == null) {
                saxParser = parser = SAX_PARSER_FACTORY.get().newSAXParser();
            }
            return parser;
        }
    }

    private static final ThreadLocal<SAXParserFactory> SAX_PARSER_FACTORY = ThreadLocal.withInitial(() -> {
        try {
            SAXParserFactory factory = SAXParserFactory.newInstance();
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            return factory;
        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }
    });

    private Map<IconGroupAndTheme, List<IconIndex.Icon>> myIcons = new ConcurrentHashMap<>();

    @Override
    public void write(BiConsumer<String, byte[]> consumer) throws IOException {
        if (myIcons.isEmpty()) {
            return;
        }

        IconIndex.IconGroupIndex.Builder iconIndexBuilder = IconIndex.IconGroupIndex.newBuilder()
            .setVersion(1);

        for (Map.Entry<IconGroupAndTheme, List<IconIndex.Icon>> entry : myIcons.entrySet()) {
            IconGroupAndTheme groupAndTheme = entry.getKey();

            IconIndex.IconGroup.Builder builder = IconIndex.IconGroup.newBuilder()
                .setTheme(groupAndTheme.themeId())
                .setId(groupAndTheme.iconGroupId())
                .addAllIcons(entry.getValue());

            iconIndexBuilder.addIconGroups(builder);
        }

        consumer.accept("icon-index.bin", iconIndexBuilder.build().toByteArray());
    }

    @Override
    public Session newSession(String jarName) {
        return new Session();
    }
}
