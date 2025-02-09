package consulo.maven.generating;

import ar.com.hjg.pngj.PngReader;
import com.github.weisj.jsvg.SVGDocument;
import com.github.weisj.jsvg.parser.SVGLoader;
import com.squareup.javapoet.*;
import consulo.maven.base.util.cache.CacheIO;
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

import javax.lang.model.element.Modifier;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.*;

/**
 * @author VISTALL
 * @since 27/11/2021
 */
@Mojo(name = "generate-icon", threadSafe = true, requiresDependencyResolution = ResolutionScope.NONE)
public class IconGeneratorMojo extends GenerateMojo {
    protected static class GenerateInfo {
        public List<File> files;

        public File baseDir;

        public String id;

        @Override
        public String toString() {
            return "GenerateInfo{" +
                "files=" + files +
                ", baseDir=" + baseDir +
                ", id='" + id + '\'' +
                '}';
        }
    }

    protected static class IconInfo {
        public String id;

        public String fieldName;

        public int width;

        public int height;

        public boolean isSVG;

        public Set<File> files = new LinkedHashSet<>();

        public Path sourcePath;
    }

    @Parameter(property = "project", defaultValue = "${project}")
    protected MavenProject myMavenProject;

    protected void execute(Log log, MavenProject mavenProject) throws MojoFailureException {
        try {
            Map<String, List<GenerateInfo>> toGenerateFiles = new LinkedHashMap<>();

            if (log.isDebugEnabled()) {
                log.debug("Analyzing: " + mavenProject.getCompileSourceRoots());
            }

            List<File> imageFiles = new ArrayList<>();

            for (Resource resource : mavenProject.getResources()) {
                File resourceDirectory = new File(resource.getDirectory());

                File iconDir = new File(resourceDirectory, "ICON-LIB");

                if (!iconDir.exists()) {
                    log.warn("IconLibrary: 'ICON-LIB' directory not exists. Path: " + iconDir);
                    return;
                }

                for (File themeId : iconDir.listFiles()) {
                    if (!themeId.isDirectory()) {
                        continue;
                    }

                    List<GenerateInfo> gen = toGenerateFiles.computeIfAbsent(themeId.getName(), (k) -> new ArrayList<>());
                    for (File iconGroup : themeId.listFiles()) {
                        if (!iconDir.isDirectory()) {
                            continue;
                        }

                        String name = iconGroup.getName();
                        if (!name.endsWith("IconGroup")) {
                            throw new MojoFailureException("IconLibrary: not endsWith IconGroup " + name);
                        }

                        List<File> files = FileUtils.getFiles(iconGroup, "**/*.svg,**/*.png", null);
                        imageFiles.addAll(files);

                        GenerateInfo g = new GenerateInfo();
                        g.baseDir = iconGroup;
                        g.id = iconGroup.getName();
                        g.files = files;

                        gen.add(g);
                    }
                }
            }

            if (log.isDebugEnabled()) {
                log.debug("Files for generate: " + toGenerateFiles);
            }

            if (toGenerateFiles.isEmpty()) {
                log.warn("IconLibrary: No file for generate");
                return;
            }

            String outputDirectory = mavenProject.getBuild().getDirectory();
            File outputDirectoryFile = new File(outputDirectory, "generated-sources/icon");

            outputDirectoryFile.mkdirs();

            CacheIO logic = new CacheIO(mavenProject, getClass().getSimpleName() + ".cache");
            if (TEST_GENERATE) {
                logic.delete();
            }
            logic.read();

            boolean isAllUp = true;

            for (File file : imageFiles) {
                if (!logic.isUpToDate(file)) {
                    isAllUp = false;
                    break;
                }
            }

            mavenProject.addCompileSourceRoot(outputDirectoryFile.getPath());

            if (isAllUp) {
                log.info("IconLibrary: is up to date");
                return;
            }

            validateGeneration(toGenerateFiles);

            for (Map.Entry<String, List<GenerateInfo>> entry : toGenerateFiles.entrySet()) {
                String themeId = entry.getKey();
                List<GenerateInfo> generateInfos = entry.getValue();

                for (GenerateInfo info : generateInfos) {
                    String id = info.id;
                    List<File> files = info.files;
                    File sourceDirectory = info.baseDir;

                    Map<String, IconInfo> icons = new TreeMap<>();

                    Path sourcePath = sourceDirectory.toPath();

                    for (File iconFile : files) {
                        String name = iconFile.getName();
                        if (name.endsWith("@2x.svg") || name.endsWith("@2x.png")) {
                            // ignore bigger icons
                            continue;
                        }

                        if (name.endsWith("_dark.svg") || name.endsWith("_dark.png")) {
                            log.info("IconLibrary: " + iconFile.getPath() + " unused dark icon");
                            continue;
                        }

                        Path parentPath = iconFile.toPath();

                        String relativePath = sourcePath.relativize(parentPath).toString();
                        if (relativePath == null) {
                            log.info("IconLibrary: " + iconFile.getPath() + " can't calculate relative path to " + sourceDirectory);
                            continue;
                        }

                        boolean isSVG = false;
                        int width, height;
                        if (relativePath.endsWith(".svg")) {
                            relativePath = relativePath.replace(".svg", "");

                            isSVG = true;

                            SVGLoader loader = new SVGLoader();

                            SVGDocument document = null;
                            try {
                                document = loader.load(iconFile.toURI().toURL());
                            }
                            catch (Exception e) {
                                throw new MojoFailureException("Failed to parse: " + iconFile, e);
                            }

                            height = (int) document.size().getHeight();
                            width = (int) document.size().getWidth();
                        }
                        else if (relativePath.endsWith(".png")) {
                            relativePath = relativePath.replace(".png", "");
                            PngReader reader = null;
                            try (InputStream stream = new FileInputStream(iconFile)) {
                                reader = new PngReader(stream);
                                width = reader.imgInfo.cols;
                                height = reader.imgInfo.rows;
                            }
                            catch (Exception e) {
                                throw new MojoFailureException("Failed to parse: " + iconFile, e);
                            }
                            finally {
                                if (reader != null) {
                                    reader.close();
                                }
                            }
                        }
                        else {
                            throw new UnsupportedOperationException(relativePath);
                        }

                        String fieldName = relativePath.replace("\\", "/").replace("/", "_");

                        IconInfo prevIconInfo = icons.get(fieldName);
                        if (icons.containsKey(fieldName)) {
                            // png can't override svg icon
                            if (prevIconInfo.isSVG && !isSVG) {
                                prevIconInfo.files.add(iconFile);
                                continue;
                            }
                        }

                        String iconId = relativePath.replace("\\", "/").replace("/", ".");
                        IconInfo iconInfo = new IconInfo();
                        iconInfo.fieldName = fieldName.replace("-", "_").toLowerCase(Locale.ROOT);
                        iconInfo.id = iconId.replace("-", "_").toLowerCase(Locale.ROOT);
                        iconInfo.sourcePath = sourcePath;

                        iconInfo.files.add(iconFile);

                        iconInfo.width = width;
                        iconInfo.isSVG = isSVG;
                        iconInfo.height = height;

                        if (prevIconInfo != null) {
                            iconInfo.files.addAll(prevIconInfo.files);
                        }

                        icons.put(fieldName, iconInfo);
                    }

                    String parentPackage = id.substring(0, id.lastIndexOf("."));
                    String name = id.substring(id.lastIndexOf(".") + 1, id.length());

                    log.info("IconLibrary: Generated file: " + id + " to " + outputDirectoryFile.getPath());

                    for (File file : files) {
                        logic.putCacheEntry(file);
                    }

                    generate(themeId, parentPackage, name, id, log, icons, outputDirectoryFile);
                }
            }

            logic.write();
        }
        catch (MojoFailureException e) {
            throw e;
        }
        catch (Exception e) {
            throw new MojoFailureException(e.getMessage(), e);
        }
    }

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        execute(getLog(), myMavenProject);
    }

    protected void validateGeneration(Map<String, List<GenerateInfo>> toGenerateFiles) throws MojoFailureException {
        if (!toGenerateFiles.containsKey("light")) {
            throw new MojoFailureException("IconLibrary: no 'light' theme icons");
        }
    }

    protected void generate(String themeId, String parentPackage, String name, String id, Log log, Map<String, IconInfo> icons, File outputDirectoryFile) throws IOException {
        if (!themeId.equals("light")) {
            return;
        }

        ClassName imageKeyClass = ClassName.get("consulo.ui.image", "ImageKey");

        List<FieldSpec> fieldSpecs = new ArrayList<>();
        List<MethodSpec> methodSpecs = new ArrayList<>();

        FieldSpec.Builder idField = FieldSpec.builder(String.class, "ID", Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL);
        idField.initializer(CodeBlock.of("$S", id));
        fieldSpecs.add(idField.build());

        for (IconInfo iconInfo : icons.values()) {
            FieldSpec.Builder fieldSpec = FieldSpec.builder(imageKeyClass, iconInfo.fieldName, Modifier.PRIVATE, Modifier.STATIC, Modifier.FINAL);
            fieldSpec.initializer(CodeBlock.builder().add("$T.of($L, $S, $L, $L)", imageKeyClass, "ID", iconInfo.id.toLowerCase(Locale.ROOT), iconInfo.width, iconInfo.height).build());
            fieldSpecs.add(fieldSpec.build());
        }

        for (IconInfo iconInfo : icons.values()) {
            MethodSpec.Builder methodSpec = MethodSpec.methodBuilder(captilizeByDot(iconInfo.id));
            methodSpec.addModifiers(Modifier.PUBLIC, Modifier.STATIC);
            methodSpec.returns(imageKeyClass);
            methodSpec.addStatement("$L", "return " + iconInfo.fieldName);

            methodSpecs.add(methodSpec.build());
        }

        TypeSpec typeSpec = TypeSpec.classBuilder(name)
            .addAnnotation(AnnotationSpec.builder(SuppressWarnings.class).addMember("value", "$S", "ALL").build())
            .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
            .addFields(fieldSpecs)
            .addMethods(methodSpecs)
            .addJavadoc("Generated code. Don't edit this class")
            .build();

        JavaFile javaFile = JavaFile.builder(parentPackage + ".icon", typeSpec)
            .build();

        javaFile.writeTo(outputDirectoryFile);
    }

    public static void main(String[] args) throws Exception {
        TEST_GENERATE = true;

        MavenProject mavenProject = new MavenProject();

        File projectDir = new File("W:\\_github.com\\consulo\\consulo\\modules\\base\\base-icon-library");
        Resource resource = new Resource();
        resource.setDirectory(new File(projectDir, "src\\main\\resources").getPath());
        Build build = new Build();
        build.addResource(resource);
        build.setOutputDirectory(new File(projectDir, "target").getAbsolutePath());
        build.setDirectory(new File(projectDir, "target").getAbsolutePath());
        mavenProject.setBuild(build);

        new IconGeneratorMojo().execute(new SystemStreamLog(), mavenProject);
    }
}
