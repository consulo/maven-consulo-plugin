package consulo.maven.packaging.processing;

import ar.com.hjg.pngj.PngReader;
import com.github.weisj.jsvg.SVGDocument;
import com.github.weisj.jsvg.parser.LoaderContext;
import com.github.weisj.jsvg.parser.SVGLoader;
import com.google.protobuf.ByteString;
import consulo.maven.protobuf.IconIndex;
import org.apache.maven.shared.utils.StringUtils;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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

    public class Session implements JarProcessorSession {

        @Override
        public void visit(String jarEntryPath, Supplier<byte[]> dataRequestor) {
            if (jarEntryPath.startsWith(ICON_LIB)) {
                int width;
                int height;

                byte[] data;
                IconIndex.IconType type;
                if (jarEntryPath.endsWith(".svg")) {
                    type = IconIndex.IconType.SVG;

                    SVGLoader loader = new SVGLoader();

                    SVGDocument document = null;
                    try {
                        document = loader.load(new ByteArrayInputStream(data = dataRequestor.get()), null, loaderContext);
                    }
                    catch (Exception e) {
                        throw new IllegalArgumentException("Failed to parse: " + jarEntryPath, e);
                    }

                    height = (int) document.size().getHeight();
                    width = (int) document.size().getWidth();
                }
                else if (jarEntryPath.endsWith(".png")) {
                    type = IconIndex.IconType.PNG;

                    PngReader reader = null;
                    try (InputStream stream = new ByteArrayInputStream(data = dataRequestor.get())) {
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

                String[] split = StringUtils.split(jarEntryPath, "/", 4);

                String themeId = split[1];
                String groupId = split[2];
                String imageId = split[3];

                int dotIndex = imageId.lastIndexOf('.');

                imageId = imageId.substring(0, dotIndex);

                IconIndex.Icon.Builder builder = IconIndex.Icon.newBuilder();
                builder.setHeight(height);
                builder.setWidth(width);
                builder.setData(ByteString.copyFrom(data));
                builder.setType(type);
                builder.setId(imageId);

                myIcons.computeIfAbsent(new IconGroupAndTheme(groupId, themeId), t -> new ArrayList<>()).add(builder.build());
            }
        }

        @Override
        public void close() {
        }
    }

    private LoaderContext loaderContext = LoaderContext.createDefault();
    private Map<IconGroupAndTheme, List<IconIndex.Icon>> myIcons = new HashMap<>();

    @Override
    public void write(BiConsumer<String, byte[]> consumer) throws IOException {
        if (myIcons.isEmpty()) {
            return;
        }

        IconIndex.IconGroupIndex.Builder iconIndexBuilder = IconIndex.IconGroupIndex.newBuilder();
        iconIndexBuilder.setVersion(1);

        for (Map.Entry<IconGroupAndTheme, List<IconIndex.Icon>> entry : myIcons.entrySet()) {
            IconIndex.IconGroup.Builder builder = IconIndex.IconGroup.newBuilder();

            IconGroupAndTheme groupAndTheme = entry.getKey();
            List<IconIndex.Icon> icons = entry.getValue();

            builder.setTheme(groupAndTheme.themeId());
            builder.setId(groupAndTheme.iconGroupId());
            builder.addAllIcons(icons);

            iconIndexBuilder.addIconGroups(builder);
        }

        IconIndex.IconGroupIndex index = iconIndexBuilder.build();

        consumer.accept("icon-index.bin", index.toByteArray());
    }

    @Override
    public Session newSession(String jarName) {
        return new Session();
    }
}
