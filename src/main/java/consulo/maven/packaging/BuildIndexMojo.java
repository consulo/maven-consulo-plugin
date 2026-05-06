package consulo.maven.packaging;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * @author VISTALL
 * @since 2024-08-28
 */
@Mojo(name = "build-index", threadSafe = true, requiresDependencyResolution = ResolutionScope.COMPILE)
public class BuildIndexMojo extends AbstractMojo {
    @Parameter(property = "project", defaultValue = "${project}", readonly = true)
    public MavenProject myProject;

    @Parameter(alias = "pluginRoots")
    protected List<File> myPluginRoots = new ArrayList<>();

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        for (File pluginRoot : myPluginRoots) {
            Path libDir = pluginRoot.toPath().resolve("lib");

            if (!Files.exists(libDir)) {
                getLog().info(libDir.toAbsolutePath() + " does not exist");
                continue;
            }

            try {
                MetaFiles metaFiles = new MetaFiles();

                try (Stream<Path> pathStream = Files.walk(libDir)) {
                    pathStream.filter(path -> Files.isRegularFile(path) && path.toString().endsWith(".jar"))
                        .parallel()
                        .forEach(jarFile -> {
                            try {
                                metaFiles.readFromJar(jarFile.toFile());
                            }
                            catch (IOException e) {
                                throw new RuntimeException(e);
                            }
                        });
                }

                metaFiles.writeIndexFiles((filePath, data) -> {
                    try {
                        File outFile = new File(pluginRoot, filePath);
                        outFile.getParentFile().mkdirs();
                        Files.write(outFile.toPath(), data);
                    }
                    catch (IOException e) {
                        throw new IllegalArgumentException(e);
                    }
                });
            }
            catch (IOException e) {
                throw new MojoFailureException(e.getMessage(), e);
            }
        }
    }
}
