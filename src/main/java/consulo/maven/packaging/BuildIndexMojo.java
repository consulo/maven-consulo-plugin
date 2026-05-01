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
import java.util.ArrayList;
import java.util.List;

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
            File libDir = new File(pluginRoot, "lib");

            if (!libDir.exists()) {
                getLog().info(libDir.getAbsolutePath() + " does not exist");
                continue;
            }

            try {
                File[] files = libDir.listFiles();

                MetaFiles metaFiles = new MetaFiles();

                for (File possibleJarFile : files) {
                    if (possibleJarFile.getName().endsWith(".jar")) {
                        metaFiles.readFromJar(possibleJarFile);
                    }
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
