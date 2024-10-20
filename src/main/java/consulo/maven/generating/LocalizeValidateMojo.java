package consulo.maven.generating;

import consulo.maven.base.util.cache.CacheIO;
import org.apache.maven.model.Resource;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;
import org.apache.maven.shared.utils.io.FileUtils;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.MessageFormat;
import java.util.*;

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

            for (File file : yamlFiles) {
                if (cache.isUpToDate(file)) {
                    log.info("Localize: Validating " + file.getPath() + " is up to date");
                    continue;
                }

                log.info("Localize: Validating file: " + file.getPath());


                Yaml yaml = new Yaml();
                try (InputStream stream = new FileInputStream(file)) {
                    Map<String, Map<String, String>> o = yaml.load(stream);

                    for (Map.Entry<String, Map<String, String>> entry : o.entrySet()) {
                        Map<String, String> value = entry.getValue();

                        String t = value.get("text");
                        String text = t == null ? "" : t;

                        try {
                            new MessageFormat(text, Locale.ENGLISH);

                            cache.putCacheEntry(file);
                        }
                        catch (Exception e) {
                            throw new MojoFailureException("Failed to parse text: " + text, e);
                        }
                    }
                }
                catch (Exception e) {
                    throw new MojoFailureException(e.getMessage(), e);
                }
            }

            cache.write();
        }
        catch (IOException e) {
            log.error(e);
        }
    }
}
