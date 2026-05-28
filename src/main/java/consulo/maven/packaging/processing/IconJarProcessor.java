package consulo.maven.packaging.processing;

import ar.com.hjg.pngj.PngReader;
import com.google.protobuf.ByteString;
import consulo.maven.jar.JarEntrySupplier;
import consulo.maven.packaging.processing.xml.SvgCleanupHandler;
import consulo.maven.packaging.processing.xml.SvgDimensionsHandler;
import consulo.maven.packaging.processing.xml.TeeHandler;
import consulo.maven.protobuf.BuildIndexCache;
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

/**
 * @author VISTALL
 * @author UNV
 * @since 2026-01-17
 */
public class IconJarProcessor implements JarProcessor {
    public static final String ICON_LIB = "ICON-LIB";

    private record IconGroupAndTheme(String groupId, String themeId) {
    }

    private record IconKey(String themeId, String groupId, String imageId) {
        @Override
        public String toString() {
            return ICON_LIB + '/' + themeId() + '/' + groupId() + '/' + imageId();
        }
    }

    private static class IconAccumulator {
        IconIndex.IconType type;
        String firstEntryPath;
        IconIndex.IconData x1;
        IconIndex.IconData x2;
    }

    class Session implements JarProcessorSession {
        private final List<IconIndex.RawIcon> myRawIcons = new ArrayList<>();

        @Override
        public void visit(JarEntrySupplier jarEntrySupplier) {
            String jarEntryPath = jarEntrySupplier.getEntryPath();
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
                    byte[] svgData = jarEntrySupplier.get();
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
                data = jarEntrySupplier.get();

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

            myRawIcons.add(IconIndex.RawIcon.newBuilder().setPath(jarEntryPath).setType(type).setIconData(iconData).build());
        }

        @Override
        public void loadFrom(BuildIndexCache.JarCache jarCache) {
            myRawIcons.clear();
            myRawIcons.addAll(jarCache.getIconsList());
        }

        @Override
        public void storeTo(BuildIndexCache.JarCache.Builder jarCacheBuilder) {
            jarCacheBuilder.addAllIcons(myRawIcons);
        }

        @Override
        public void close() {
            Map<IconKey, IconAccumulator> accumulators = new HashMap<>();

            for (IconIndex.RawIcon rawIcon : myRawIcons) {
                String[] split = StringUtils.split(rawIcon.getPath(), "/", 4);

                String imageId = split[3];

                int dotIndex = imageId.lastIndexOf('.');
                imageId = imageId.substring(0, dotIndex);

                boolean is2x = imageId.endsWith("@2x");
                if (is2x) {
                    imageId = imageId.substring(0, imageId.length() - 3);
                }

                imageId = imageId.replace('\\', '/').replace('/', '.').replace('-', '_').toLowerCase(Locale.ROOT);

                IconKey key = new IconKey(split[1], split[2], imageId);

                IconAccumulator acc = accumulators.computeIfAbsent(key, k -> new IconAccumulator());

                if (acc.type == null) {
                    acc.type = rawIcon.getType();
                    acc.firstEntryPath = rawIcon.getPath();
                }
                else if (acc.type != rawIcon.getType()) {
                    throw new IllegalStateException(
                        "Icon type mismatch for " + key
                            + ": " + acc.type + " from " + acc.firstEntryPath
                            + ", " + rawIcon.getType() + " from " + rawIcon.getPath()
                    );
                }

                if (is2x) {
                    if (acc.x2 != null) {
                        throw new IllegalStateException("Duplicate @2x icon: " + rawIcon.getPath());
                    }
                    acc.x2 = rawIcon.getIconData();
                }
                else {
                    if (acc.x1 != null) {
                        throw new IllegalStateException("Duplicate icon: " + rawIcon.getPath());
                    }
                    acc.x1 = rawIcon.getIconData();
                }
            }

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
                .setId(groupAndTheme.groupId())
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
