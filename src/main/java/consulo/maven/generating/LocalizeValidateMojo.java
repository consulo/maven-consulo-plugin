package consulo.maven.generating;

import consulo.compiler.apt.shared.generation.GeneratedElementFactory;
import consulo.compiler.apt.shared.generator.LocalizeGenerator;
import consulo.maven.base.util.cache.CacheIO;
import maven.bnf.consulo.util.io.FileUtil;
import org.apache.maven.model.Resource;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;
import org.apache.maven.shared.utils.io.FileUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * @author VISTALL
 * @see LocalizeGeneratorMojo
 * @since 2024-10-20
 */
@Mojo(name = "validate-localize", threadSafe = true, requiresDependencyResolution = ResolutionScope.NONE)
public class LocalizeValidateMojo extends GenerateMojo {
    @Parameter(property = "project", defaultValue = "${project}")
    private MavenProject myMavenProject;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        validate(getLog(), myMavenProject);
    }

    private static void validate(Log log, MavenProject mavenProject) throws MojoExecutionException, MojoFailureException {
        try {
            List<File> yamlFiles = new ArrayList<>();

            if (log.isDebugEnabled()) {
                log.debug("Analyzing: " + mavenProject.getCompileSourceRoots());
            }

            for (Resource resource : mavenProject.getResources()) {
                File srcDirectory = new File(resource.getDirectory());

                File localizeDir = new File(srcDirectory, "LOCALIZE-LIB");

                for (File localeDir : localizeDir.listFiles()) {
                    if (!localeDir.isDirectory()) {
                        continue;
                    }

                    List<File> files = FileUtils.getFiles(localeDir, "*.yaml", null);
                    for (File file : files) {
                        yamlFiles.add(file);
                    }
                }
            }

            if (log.isDebugEnabled()) {
                log.debug("Files for validate: " + yamlFiles);
            }

            if (yamlFiles.isEmpty()) {
                return;
            }

            CacheIO cache = new CacheIO(mavenProject, "validate-localize.cache");
            if (TEST_GENERATE) {
                cache.delete();
            }
            cache.read();

            LocalizeGenerator generator = new LocalizeGenerator(GeneratedElementFactory.first());

            for (File file : yamlFiles) {
                if (cache.isUpToDate(file)) {
                    log.info("Localize: Validating " + file.getPath() + " is up to date");
                    continue;
                }

                log.info("Localize: Validating file: " + file.getPath());

                String localizeFullPath = FileUtil.getNameWithoutExtension(file.getName());

                generator.parse(localizeFullPath, file.toPath());
            }

            cache.write();
        }
        catch (Exception e) {
            throw new MojoFailureException(null, e);
        }
    }
}
