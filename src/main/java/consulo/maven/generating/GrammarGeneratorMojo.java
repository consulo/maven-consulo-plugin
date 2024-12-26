package consulo.maven.generating;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import consulo.annotation.access.RequiredReadAction;
import consulo.application.Application;
import consulo.component.internal.inject.InjectingContainerBuilder;
import consulo.disposer.AutoDisposable;
import consulo.language.inject.InjectedLanguageManager;
import consulo.language.psi.PsiManager;
import consulo.maven.base.util.cache.CacheIO;
import consulo.project.Project;
import consulo.test.light.LightApplicationBuilder;
import consulo.test.light.LightProjectBuilder;
import consulo.test.light.impl.LightFileTypeRegistry;
import consulo.util.lang.Couple;
import consulo.util.lang.StringUtil;
import consulo.virtualFileSystem.StandardFileSystems;
import consulo.virtualFileSystem.VirtualFile;
import consulo.virtualFileSystem.fileType.FileTypeRegistry;
import org.apache.maven.model.Build;
import org.apache.maven.model.Resource;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugin.logging.SystemStreamLog;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;
import org.apache.maven.shared.utils.io.FileUtils;
import org.intellij.grammar.BnfFileType;
import org.intellij.grammar.generator.Case;
import org.intellij.grammar.generator.ParserGenerator;
import org.intellij.grammar.generator.ParserGeneratorUtil;
import org.intellij.grammar.java.JavaHelper;
import org.intellij.grammar.psi.BnfFile;
import org.intellij.grammar.psi.BnfRule;

import javax.annotation.Nonnull;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author VISTALL
 * @since 2018-06-18
 */
@Mojo(name = "generate-parsers", threadSafe = true, requiresDependencyResolution = ResolutionScope.NONE)
public class GrammarGeneratorMojo extends AbstractMojo {
    @Parameter(property = "project", defaultValue = "${project}")
    private MavenProject myMavenProject;

    public static void main(String[] args) throws Exception {
        MavenProject mavenProject = new MavenProject();

        File projectDir = new File("W:\\ConsulorRepos\\consulo-devkit\\grammar-kit-core");
        Resource resource = new Resource();
        resource.setDirectory(new File(projectDir, "src\\main\\resources").getPath());
        Build build = new Build();
        build.addResource(resource);
        build.setSourceDirectory(new File(projectDir, "src\\main\\java").getPath());
        build.setOutputDirectory(new File(projectDir, "target").getAbsolutePath());
        build.setDirectory(new File(projectDir, "target").getAbsolutePath());
        mavenProject.setBuild(build);

        GrammarGeneratorMojo grammarGeneratorMojo = new GrammarGeneratorMojo();
        grammarGeneratorMojo.myMavenProject = mavenProject;
        grammarGeneratorMojo.setLog(new SystemStreamLog());

        grammarGeneratorMojo.execute();
    }

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        try {
            String sourceDirectory = myMavenProject.getBuild().getSourceDirectory();
            File sourceDirectoryFile = new File(sourceDirectory);
            if (!sourceDirectoryFile.exists()) {
                getLog().info(sourceDirectory + " is not exists");
                return;
            }

            List<File> files = FileUtils.getFiles(sourceDirectoryFile, "**/*.bnf", null);
            if (files.isEmpty()) {
                return;
            }

            String outputDirectory = myMavenProject.getBuild().getDirectory();
            File outputDirectoryFile = new File(outputDirectory, "generated-sources/parsers");
            if (!outputDirectoryFile.exists()) {
                outputDirectoryFile.mkdirs();
            }


            CacheIO logic = new CacheIO(myMavenProject, "localize.cache");
            logic.read();

            for (File file : files) {
                if (logic.isUpToDate(file)) {
                    getLog().info("Grammar: " + file.getPath() + " is up to date");
                    continue;
                }

                getLog().info("Generated file: " + file.getPath() + " to " + outputDirectoryFile.getPath());

                runGenerator(file.getPath(), outputDirectoryFile.getPath(), sourceDirectory, getLog());

                logic.putCacheEntry(file);
            }

            myMavenProject.addCompileSourceRoot(outputDirectoryFile.getPath());
            
            logic.write();
        }
        catch (Exception e) {
            getLog().error(e);
        }
    }

    @RequiredReadAction
    private static void runGenerator(String filePath, String directoryToGenerate, @Nonnull String sourceDirectory, Log log) throws Exception {
        try (AutoDisposable rootDisposable = AutoDisposable.newAutoDisposable()) {
            LightApplicationBuilder applicationBuilder = LightApplicationBuilder.create(rootDisposable);

            Application application = applicationBuilder.build();

            LightFileTypeRegistry fileTypeRegistry = (LightFileTypeRegistry) FileTypeRegistry.getInstance();
            fileTypeRegistry.registerFileType(BnfFileType.INSTANCE, "bnf");

            LightProjectBuilder projectBuilder = LightProjectBuilder.create(application, new LightProjectBuilder.DefaultRegistrator() {
                @Override
                public void registerServices(@Nonnull InjectingContainerBuilder builder) {
                    super.registerServices(builder);

                    builder.bind(InjectedLanguageManager.class).forceSingleton().to(InjectedLanguageManagerStub.class);

                    builder.bind(JavaHelper.class).forceSingleton().to(new JavaParserJavaHelper(sourceDirectory, directoryToGenerate, log));
                }
            });

            Project project = projectBuilder.build();
            PsiManager psiManager = PsiManager.getInstance(project);

            File ioFile = new File(filePath);
            VirtualFile fileByIoFile = StandardFileSystems.local().findFileByPath(ioFile.getPath());
            if (fileByIoFile == null) {
                System.out.println("File not exists: " + filePath);
                System.exit(-1);
                return;
            }

            BnfFile file = (BnfFile) psiManager.findFile(fileByIoFile);

            List<BnfRule> rules = file.getRules();

            BiMap<String, String> names = HashBiMap.create();
            Map<String, String> baseClassNames = new HashMap<>();
            for (BnfRule rule : rules) {
                ParserGeneratorUtil.NameFormat prefix = ParserGeneratorUtil.getPsiClassFormat(file);
                ParserGeneratorUtil.NameFormat prefixImpl = ParserGeneratorUtil.getPsiImplClassFormat(file);

                Couple<String> qualifiedRuleClassNames = ParserGeneratorUtil.getQualifiedRuleClassName(rule);
                String qualifiedRuleClassName = qualifiedRuleClassNames.getFirst();
                names.put(ParserGeneratorUtil.toIdentifier(rule.getName(), prefix, Case.CAMEL), qualifiedRuleClassName);
                names.put(ParserGeneratorUtil.toIdentifier(rule.getName(), prefixImpl, Case.CAMEL), qualifiedRuleClassNames.getSecond());

                List<String> ruleClasses = new ArrayList<>(ParserGeneratorUtil.getRuleClasses(rule));

                String baseClass = ruleClasses.size() >= 4 ? ruleClasses.get(3) : ruleClasses.get(ruleClasses.size() - 1);

                baseClassNames.put(qualifiedRuleClassName, getFirstImplClass(baseClass));
            }

            JavaParserJavaHelper helper = (JavaParserJavaHelper) JavaHelper.getJavaHelper(file);

            helper.setRuleClassNames(names);
            helper.setBaseClassNames(baseClassNames);

            new ParserGenerator(file, ioFile.getParentFile().getPath(), directoryToGenerate, "").generate();
        }
        catch (Throwable e) {
            throw new MojoFailureException(e.getMessage(), e);
        }
    }

    private static String getFirstImplClass(String clazz) {
        if (clazz.contains(",")) {
            List<String> split = StringUtil.split(clazz, ",");
            return split.get(0).trim();
        }
        return clazz;
    }
}
