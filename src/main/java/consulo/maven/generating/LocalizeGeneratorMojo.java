package consulo.maven.generating;

import consulo.compiler.apt.shared.generation.GeneratedClass;
import consulo.compiler.apt.shared.generation.GeneratedElementFactory;
import consulo.compiler.apt.shared.generator.LocalizeGenerator;
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
import org.apache.maven.shared.utils.PathTool;
import org.apache.maven.shared.utils.StringUtils;
import org.apache.maven.shared.utils.io.FileUtils;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

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
            List<Map.Entry<File, List<LocalizeGenerator.SubFile>>> toGenerateFiles = new ArrayList<>();

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
                    toGenerateFiles.add(new AbstractMap.SimpleEntry<>(file, new ArrayList<>()));
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

            for (Map.Entry<File, List<LocalizeGenerator.SubFile>> entry : toGenerateFiles) {
                File file = entry.getKey();
                String name = file.getName();

                String fileNameNoExtension = name.substring(0, name.length() - 5);

                List<LocalizeGenerator.SubFile> result = new ArrayList<>();

                File subDir = new File(file.getParent(), fileNameNoExtension);
                if (subDir.exists()) {
                    List<File> subFiles = FileUtils.getFiles(subDir, null, null);
                    for (File subFile : subFiles) {
                        String relativePath = PathTool.getRelativeFilePath(subDir.getPath(), subFile.getPath());
                        if (relativePath == null) {
                            continue;
                        }
                        
                        relativePath = relativePath.replace("\\", "/");

                        int extension = relativePath.lastIndexOf('.');
                        if (extension != -1) {
                            relativePath = relativePath.substring(0, extension);
                        }

                        relativePath = relativePath.toLowerCase(Locale.ROOT);

                        String[] parts = StringUtils.split(relativePath, "/");

                        result.add(new LocalizeGenerator.SubFile(List.of(parts), subFile.toPath()));
                    }
                }

                if (!result.isEmpty()) {
                    entry.setValue(result);
                }
            }

            String outputDirectory = mavenProject.getBuild().getDirectory();
            File outputDirectoryFile = new File(outputDirectory, "generated-sources/localize");

            outputDirectoryFile.mkdirs();

            Set<String> oldCache = new TreeSet<>();

            Path cachePath = Paths.get(mavenProject.getBuild().getDirectory(), "maven-status", "maven-consulo-plugin", "localize-cache.lst");
            if (Files.exists(cachePath)) {
                oldCache.addAll(Files.readAllLines(cachePath));
            }

            if (TEST_GENERATE) {
                oldCache.clear();
            }

            mavenProject.addCompileSourceRoot(outputDirectoryFile.getPath());

            LocalizeGenerator generator = new LocalizeGenerator(GeneratedElementFactory.first());

            Set<String> newCache = new TreeSet<>();

            for (Map.Entry<File, List<LocalizeGenerator.SubFile>> info : toGenerateFiles) {
                File file = info.getKey();
                List<LocalizeGenerator.SubFile> subFiles = info.getValue();

                LocalizeNewCacheEntry entry = new LocalizeNewCacheEntry(file, subFiles);
                if (oldCache.contains(entry.toString())) {
                    newCache.add(entry.toString());

                    log.info("Localize: " + file.getPath() + " is up to date");
                    continue;
                }

                String localizeFullPath = FileUtil.getNameWithoutExtension(file.getName());

                GeneratedClass generatedClass = generator.parse(localizeFullPath, file.toPath(), new LinkedHashSet<>(subFiles));

                generatedClass.write(outputDirectoryFile.toPath());

                newCache.add(entry.toString());
            }

            Files.deleteIfExists(cachePath);

            Files.createDirectories(cachePath.getParent());
            
            Files.createFile(cachePath);

            Files.write(cachePath, newCache);
        }
        catch (Exception e) {
            throw new MojoFailureException(null, e);
        }
    }

    public static void main(String[] args) throws Exception {
        TEST_GENERATE = true;

        MavenProject mavenProject = new MavenProject();

        File projectDir = new File("W:\\ConsulorRepos\\consulo\\modules\\base\\ide-api");
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
