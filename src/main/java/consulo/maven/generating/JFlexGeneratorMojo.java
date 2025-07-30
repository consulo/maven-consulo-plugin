package consulo.maven.generating;

import consulo.maven.base.util.cache.CacheIO;
import jflex.core.OptionUtils;
import jflex.generator.LexGenerator;
import jflex.option.Options;
import jflex.option.OutputMode;
import jflex.skeleton.Skeleton;
import org.apache.maven.model.Build;
import org.apache.maven.model.Resource;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;
import org.apache.maven.shared.utils.io.FileUtils;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * @author VISTALL
 * @since 2018-06-19
 */
@Mojo(name = "generate-lexers", threadSafe = true, requiresDependencyResolution = ResolutionScope.NONE)
public class JFlexGeneratorMojo extends AbstractMojo {
    @Parameter(property = "project", defaultValue = "${project}")
    private MavenProject myMavenProject;

    public JFlexGeneratorMojo() {
        Options.no_constructor = true;
        Options.no_backup = true;
        // integrated by default Options.char_at = true;
    }

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        try {
            List<Map.Entry<File, File>> toGenerateFiles = new ArrayList<>();

            for (String srcDir : myMavenProject.getCompileSourceRoots()) {
                File srcDirectory = new File(srcDir);

                List<File> files = FileUtils.getFiles(srcDirectory, "**/*.flex", null, true);
                for (File file : files) {
                    toGenerateFiles.add(new AbstractMap.SimpleImmutableEntry<>(file, srcDirectory));
                }
            }

            if (toGenerateFiles.isEmpty()) {
                return;
            }

            String outputDirectory = myMavenProject.getBuild().getDirectory();
            File outputDirectoryFile = new File(outputDirectory, "generated-sources/lexers");

            outputDirectoryFile.mkdirs();

            CacheIO logic = new CacheIO(myMavenProject, "jflex-generate.cache");

            logic.read();

            myMavenProject.addCompileSourceRoot(outputDirectoryFile.getPath());

            Options.encoding = StandardCharsets.UTF_8;

            for (Map.Entry<File, File> info : toGenerateFiles) {
                File file = info.getKey();
                File sourceDirectory = info.getValue();

                if (logic.isUpToDate(file)) {
                    getLog().info("JFlex: " + file.getPath() + " is up to date");
                    continue;
                }

                Path sourcePath = sourceDirectory.toPath();

                Path parentPath = file.getParentFile().toPath();

                String relativePath = sourcePath.relativize(parentPath).toString();

                if (relativePath != null) {
                    File outDirWithPackage = new File(outputDirectoryFile, relativePath);
                    outDirWithPackage.mkdirs();
                    OptionUtils.setDir(outDirWithPackage);
                }

                getLog().info("JFlex: Generated file: " + file.getPath() + " to " + outputDirectoryFile.getPath());

                logic.putCacheEntry(file);

                File skeletonFile = new File(file.getParent(), file.getName() + ".skeleton");
                if (skeletonFile.exists()) {
                    try (BufferedReader stream = new BufferedReader(new InputStreamReader(Files.newInputStream(skeletonFile.toPath()), StandardCharsets.UTF_8))) {
                        Skeleton.readSkel(stream);
                    }
                    Options.no_constructor = true;
                }
                else {
                    File marker = new File(file.getParentFile(), file.getName() + ".idea");
                    // marker for using old IDEA skeleton or Consulo skeleton
                    boolean ideaMarker = marker.exists();
                    String name = ideaMarker ? "/META-INF/skeleton/idea-jflex.skeleton" : "/META-INF/skeleton/consulo-jflex.skeleton";
                    try (BufferedReader stream = new BufferedReader(new InputStreamReader(getClass().getResourceAsStream(name), StandardCharsets.UTF_8))) {
                        Skeleton.readSkel(stream);
                    }
                    Options.no_constructor = !ideaMarker;
                }

                Options.setRootDirectory(sourceDirectory);

                Options.output_mode = OutputMode.JAVA;

                new LexGenerator(file).generate();
            }

            logic.write();
        }
        catch (Exception e) {
            getLog().error(e);
        }
    }

    public static void main(String[] args) throws Exception {
        File projectDir = new File("W:\\_github.com\\consulo\\consulo-csharp\\csharp-psi-impl");


        MavenProject mavenProject = new MavenProject();

        mavenProject.addCompileSourceRoot(new File(projectDir, "src").getAbsolutePath());
        Resource resource = new Resource();
        resource.setDirectory(new File(projectDir, "src\\main\\resources").getPath());
        Build build = new Build();
        build.addResource(resource);
        build.setOutputDirectory(new File(projectDir, "target").getAbsolutePath());
        build.setDirectory(new File(projectDir, "target").getAbsolutePath());
        mavenProject.setBuild(build);


        JFlexGeneratorMojo mojo = new JFlexGeneratorMojo();
        mojo.myMavenProject = mavenProject;

        mojo.execute();
    }
}
