package consulo.maven.generating;

import consulo.compiler.apt.shared.generation.GeneratedClass;
import consulo.compiler.apt.shared.generation.GeneratedElementFactory;
import consulo.compiler.apt.shared.generator.LocalizeGenerator;
import consulo.maven.base.util.cache.CacheIO;
import consulo.util.io.FileUtil;
import org.apache.maven.model.Build;
import org.apache.maven.model.Resource;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugin.logging.SystemStreamLog;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;
import org.apache.maven.shared.utils.io.FileUtils;

import java.io.File;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * @author VISTALL
 * @since 2020-05-21
 */
@Mojo(name = "generate-localize", threadSafe = true, requiresDependencyResolution = ResolutionScope.NONE)
public class LocalizeGeneratorMojo extends GenerateMojo {
    @Parameter(property = "project", defaultValue = "${project}")
    private MavenProject myMavenProject;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        generate(getLog(), myMavenProject);
    }

    private static void generate(Log log, MavenProject mavenProject) throws MojoExecutionException, MojoFailureException {
        try {
            List<Map.Entry<File, File>> toGenerateFiles = new ArrayList<>();

            if (log.isDebugEnabled()) {
                log.debug("Analyzing: " + mavenProject.getCompileSourceRoots());
            }

            for (Resource resource : mavenProject.getResources()) {
                File srcDirectory = new File(resource.getDirectory());

                File localizeDir = new File(srcDirectory, "LOCALIZE-LIB");

                File enUSDir = new File(localizeDir, "en_US");
                if (!enUSDir.exists()) {
                    // skip not en_US localize
                    continue;
                }

                List<File> files = FileUtils.getFiles(enUSDir, "*.yaml", null);
                for (File file : files) {
                    toGenerateFiles.add(new AbstractMap.SimpleImmutableEntry<>(file, srcDirectory));
                }

                if (files.isEmpty()) {
                    throw new MojoFailureException("LocalizeLibrary: " + enUSDir + " exists, but no files.");
                }
            }

            if (log.isDebugEnabled()) {
                log.debug("Files for generate: " + toGenerateFiles);
            }

            if (toGenerateFiles.isEmpty()) {
                return;
            }

            String outputDirectory = mavenProject.getBuild().getDirectory();
            File outputDirectoryFile = new File(outputDirectory, "generated-sources/localize");

            outputDirectoryFile.mkdirs();

            CacheIO logic = new CacheIO(mavenProject, "localize.cache");
            if (TEST_GENERATE) {
                logic.delete();
            }
            logic.read();

            mavenProject.addCompileSourceRoot(outputDirectoryFile.getPath());

            LocalizeGenerator generator = new LocalizeGenerator(GeneratedElementFactory.first());

            for (Map.Entry<File, File> info : toGenerateFiles) {
                File file = info.getKey();
                File sourceDirectory = info.getValue();

                if (logic.isUpToDate(file)) {
                    log.info("Localize: " + file.getPath() + " is up to date");
                    continue;
                }

                String localizeFullPath = FileUtil.getNameWithoutExtension(file.getName());

                GeneratedClass generatedClass = generator.parse(localizeFullPath, file.toPath());

                generatedClass.write(outputDirectoryFile.toPath());
            }

            logic.write();
        }
        catch (Exception e) {
            throw new MojoFailureException(null, e);
        }
    }

    public static void main(String[] args) throws Exception {
        TEST_GENERATE = true;

        MavenProject mavenProject = new MavenProject();

        File projectDir = new File("W:\\ConsulorRepos\\consulo-handlebars");
        Resource resource = new Resource();
        resource.setDirectory(new File(projectDir, "src\\main\\resources").getPath());
        Build build = new Build();
        build.addResource(resource);
        build.setOutputDirectory(new File(projectDir, "target").getAbsolutePath());
        build.setDirectory(new File(projectDir, "target").getAbsolutePath());
        mavenProject.setBuild(build);

        generate(new SystemStreamLog(), mavenProject);
    }
}
