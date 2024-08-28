package consulo.maven.packaging;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * @author VISTALL
 * @since 2024-08-28
 */
public class IndexFileBuilder {
    public static List<String> buildIndex(Path path) throws IOException {
        Path libDir = path.resolve("lib");

        if (!Files.exists(libDir)) {
            return List.of();
        }

        List<String> rows = new ArrayList<>();

        Files.walkFileTree(libDir, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Path fileName = file.getFileName();

                String fileNameString = fileName.toString();
                if (fileNameString.endsWith(".jar")) {
                    rows.add("#" + fileNameString);

                    try (FileSystem jar = FileSystems.newFileSystem(file, Map.of())) {
                        Iterable<Path> rootDirectories = jar.getRootDirectories();

                        for (Path rootDirectory : rootDirectories) {
                            List<Path> list = Files.walk(rootDirectory).toList();

                            list.forEach(child -> {
                                String childStr = child.toString();
                                if (childStr.length() == 1 && childStr.charAt(0) == '/') {
                                    return;
                                }
                                
                                rows.add(childStr);
                            });
                        }
                    }
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                if (dir.equals(libDir)) {
                    return FileVisitResult.CONTINUE;
                }
                return FileVisitResult.SKIP_SUBTREE;
            }
        });

        return rows;
    }

    public static void main(String[] args) throws Exception {
        Path path = Path.of("W:\\Consulo3\\settings\\config\\plugins\\consulo.java");

        List<String> strings = buildIndex(path);

        System.out.println();
    }
}
