package consulo.maven.packaging;

import consulo.maven.jar.JarSupplier;
import consulo.maven.packaging.processing.IconJarProcessor;
import consulo.maven.packaging.processing.JarIndexProcessor;
import consulo.maven.packaging.processing.JarProcessorGroup;
import consulo.maven.packaging.processing.LocalizeJarProcessor;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * @author VISTALL
 * @author UNV
 * @since 2024-08-28
 */
@Mojo(name = "build-index", threadSafe = true, requiresDependencyResolution = ResolutionScope.COMPILE)
public class BuildIndexMojo extends AbstractMojo {
    public static final String CACHE_FILE = "maven-consulo-plugin/build-index.cache";

    @Parameter(property = "project", defaultValue = "${project}", readonly = true)
    public MavenProject myProject;

    @Parameter(alias = "pluginRoots")
    protected List<File> myPluginRoots = new ArrayList<>();

    @Parameter(defaultValue = "${project.build.directory}", required = true, readonly = true)
    protected File myTargetDir;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        for (File pluginRootFile : myPluginRoots) {
            Path pluginRoot = pluginRootFile.toPath();
            Path libDir = pluginRoot.resolve("lib");

            if (!Files.exists(libDir)) {
                getLog().info(libDir.toAbsolutePath() + " does not exist");
                continue;
            }

            try {
                JarProcessorGroup metaFiles =
                    new JarProcessorGroup(new JarIndexProcessor(), new IconJarProcessor(), new LocalizeJarProcessor());

                Path cacheFile = myTargetDir.toPath().resolve(CACHE_FILE);
                if (Files.exists(cacheFile)) {
                    metaFiles.readCache(() -> {
                        try {
                            return Files.readAllBytes(cacheFile);
                        }
                        catch (IOException e) {
                            throw new UncheckedIOException(e);
                        }
                    });
                }

                try (Stream<Path> pathStream = Files.walk(libDir)) {
                    pathStream.filter(path -> Files.isRegularFile(path) && path.toString().endsWith(".jar"))
                        .parallel()
                        .forEach(jarFile -> {
                            try {
                                metaFiles.readFromJar(JarSupplier.of(jarFile.toFile()));
                            }
                            catch (IOException e) {
                                throw new UncheckedIOException(e);
                            }
                        });
                }

                metaFiles.writeIndexFiles((filePath, data) -> {
                    try {
                        Path outFile = pluginRoot.resolve(filePath);
                        Files.createDirectories(outFile.getParent());
                        Files.write(outFile, data);
                    }
                    catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                });

                Files.createDirectories(cacheFile.getParent());

                metaFiles.writeCache(bytes -> {
                    try {
                        Files.write(cacheFile, bytes);
                    }
                    catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                });
            }
            catch (IOException | UncheckedIOException e) {
                throw new MojoFailureException(e.getMessage(), e);
            }
        }
    }
}
