package consulo.maven.packaging.processing;

import ar.com.hjg.pngj.PngReader;
import com.github.weisj.jsvg.SVGDocument;
import com.github.weisj.jsvg.parser.LoaderContext;
import com.github.weisj.jsvg.parser.SVGLoader;
import com.google.protobuf.ByteString;
import consulo.maven.protobuf.IconIndex;
import org.apache.maven.shared.utils.StringUtils;
import org.jdom.Comment;
import org.jdom.Content;
import org.jdom.Document;
import org.jdom.Parent;
import org.jdom.input.SAXBuilder;
import org.jdom.input.sax.XMLReaders;
import org.jdom.output.Format;
import org.jdom.output.XMLOutputter;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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
    }

    private record RawEntry(
        String jarEntryPath,
        String themeId,
        String groupId,
        String imageId,
        boolean is2x,
        IconIndex.IconType type,
        byte[] data
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

            if (jarEntryPath.endsWith(".svg")) {
                type = IconIndex.IconType.SVG;
                try {
                    data = cleanupXml(dataRequestor.get());
                }
                catch (Exception e) {
                    throw new IllegalArgumentException("Failed to clean up: " + jarEntryPath, e);
                }
            }
            else if (jarEntryPath.endsWith(".png")) {
                type = IconIndex.IconType.PNG;
                data = dataRequestor.get();
            }
            else {
                return;
            }

            String[] split = StringUtils.split(jarEntryPath, "/", 4);

            String themeId = split[1];
            String groupId = split[2];
            String imageId = split[3];

            int dotIndex = imageId.lastIndexOf('.');
            imageId = imageId.substring(0, dotIndex);

            boolean is2x = imageId.endsWith("@2x");
            if (is2x) {
                imageId = imageId.substring(0, imageId.length() - 3);
            }

            imageId = imageId.replace('\\', '/').replace('/', '.').replace('-', '_').toLowerCase(Locale.ROOT);

            myEntries.add(new RawEntry(jarEntryPath, themeId, groupId, imageId, is2x, type, data));
        }

        @Override
        public void close() {
            Map<IconKey, IconAccumulator> accumulators = new HashMap<>();

            for (RawEntry entry : myEntries) {
                int width;
                int height;

                if (entry.type() == IconIndex.IconType.SVG) {
                    SVGLoader loader = new SVGLoader();

                    SVGDocument document;
                    try {
                        document = loader.load(new ByteArrayInputStream(entry.data()), null, loaderContext);
                    }
                    catch (Exception e) {
                        throw new IllegalArgumentException("Failed to parse: " + entry.jarEntryPath(), e);
                    }

                    height = (int) document.size().getHeight();
                    width = (int) document.size().getWidth();
                }
                else {
                    PngReader reader = null;
                    try (InputStream stream = new ByteArrayInputStream(entry.data())) {
                        reader = new PngReader(stream);
                        width = reader.imgInfo.cols;
                        height = reader.imgInfo.rows;
                    }
                    catch (Exception e) {
                        throw new IllegalArgumentException("Failed to parse: " + entry.jarEntryPath(), e);
                    }
                    finally {
                        if (reader != null) {
                            reader.close();
                        }
                    }
                }

                IconIndex.IconData iconData = IconIndex.IconData.newBuilder()
                    .setHeight(height)
                    .setWidth(width)
                    .setData(ByteString.copyFrom(entry.data()))
                    .build();

                IconKey key = new IconKey(entry.themeId(), entry.groupId(), entry.imageId());
                IconAccumulator acc = accumulators.computeIfAbsent(key, k -> new IconAccumulator());

                if (acc.type == null) {
                    acc.type = entry.type();
                    acc.firstEntryPath = entry.jarEntryPath();
                }
                else if (acc.type != entry.type()) {
                    throw new IllegalStateException(
                        "Icon type mismatch for " + entry.themeId() + "/" + entry.groupId() + "/" + entry.imageId()
                            + ": " + acc.type + " from " + acc.firstEntryPath
                            + ", " + entry.type() + " from " + entry.jarEntryPath()
                    );
                }

                if (entry.is2x()) {
                    if (acc.x2 != null) {
                        throw new IllegalStateException("Duplicate @2x icon: " + entry.jarEntryPath());
                    }
                    acc.x2 = iconData;
                }
                else {
                    if (acc.x1 != null) {
                        throw new IllegalStateException("Duplicate icon: " + entry.jarEntryPath());
                    }
                    acc.x1 = iconData;
                }
            }

            myEntries.clear();

            for (Map.Entry<IconKey, IconAccumulator> entry : accumulators.entrySet()) {
                IconKey key = entry.getKey();
                IconAccumulator acc = entry.getValue();

                if (acc.x1 == null) {
                    throw new IllegalStateException(
                        "Missing x1 icon for " + key.themeId() + "/" + key.groupId() + "/" + key.imageId() + " (only @2x found)"
                    );
                }

                IconIndex.Icon.Builder iconBuilder = IconIndex.Icon.newBuilder()
                    .setId(key.imageId())
                    .setType(acc.type)
                    .setX1(acc.x1);
                if (acc.x2 != null) {
                    iconBuilder.setX2(acc.x2);
                }

                myIcons.computeIfAbsent(new IconGroupAndTheme(key.groupId(), key.themeId()), t -> new ArrayList<>())
                    .add(iconBuilder.build());
            }
        }
    }

    private LoaderContext loaderContext = LoaderContext.createDefault();
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

    private byte[] cleanupXml(byte[] data) throws Exception {
        try (ByteArrayInputStream in = new ByteArrayInputStream(data); ByteArrayOutputStream stream = new ByteArrayOutputStream()) {
            SAXBuilder builder = new SAXBuilder();
            builder.setXMLReaderFactory(XMLReaders.NONVALIDATING);

            Document document = builder.build(in);

            removeComments(document);

            XMLOutputter outputter = new XMLOutputter();
            outputter.setFormat(Format.getCompactFormat());
            outputter.output(document, stream);

            return stream.toByteArray();
        }
    }

    private void removeComments(Parent parent) {
        List<Content> contents = new ArrayList<>(parent.getContent());

        for (Content child : contents) {
            if (child instanceof Comment) {
                child.detach();
            }
            else if (child instanceof Parent childParent) {
                removeComments(childParent);
            }
        }
    }

    @Override
    public Session newSession(String jarName) {
        return new Session();
    }
}
