package consulo.maven.generating;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import consulo.maven.base.util.cache.CacheIO;
import maven.bnf.consulo.annotation.access.RequiredReadAction;
import maven.bnf.consulo.application.Application;
import maven.bnf.consulo.component.internal.inject.InjectingContainerBuilder;
import maven.bnf.consulo.devkit.grammarKit.generator.GenerateTarget;
import maven.bnf.consulo.disposer.AutoDisposable;
import maven.bnf.consulo.language.inject.InjectedLanguageManager;
import maven.bnf.consulo.language.psi.PsiManager;
import maven.bnf.consulo.project.Project;
import maven.bnf.consulo.test.light.LightApplicationBuilder;
import maven.bnf.consulo.test.light.LightProjectBuilder;
import maven.bnf.consulo.test.light.impl.LightFileTypeRegistry;
import maven.bnf.consulo.util.lang.Couple;
import maven.bnf.consulo.util.lang.StringUtil;
import maven.bnf.consulo.virtualFileSystem.StandardFileSystems;
import maven.bnf.consulo.virtualFileSystem.VirtualFile;
import maven.bnf.consulo.virtualFileSystem.fileType.FileTypeRegistry;
import maven.bnf.org.intellij.grammar.BnfFileType;
import maven.bnf.org.intellij.grammar.generator.Case;
import maven.bnf.org.intellij.grammar.generator.ParserGenerator;
import maven.bnf.org.intellij.grammar.generator.ParserGeneratorUtil;
import maven.bnf.org.intellij.grammar.java.JavaHelper;
import maven.bnf.org.intellij.grammar.psi.BnfFile;
import maven.bnf.org.intellij.grammar.psi.BnfRule;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.apache.maven.shared.utils.io.FileUtils;

import javax.annotation.Nonnull;
import java.io.File;
import java.util.*;

/**
 * @author VISTALL
 * @since 2018-06-18
 */
public abstract class AbstractGrammarGeneratorMojo extends AbstractMojo {
    @Parameter(property = "project", defaultValue = "${project}")
    protected MavenProject myMavenProject;

    @Parameter(property = "externalGrammars")
    public List<File> externalGrammars = new ArrayList<>();

    public abstract String getOutputDirName();

    public abstract Set<GenerateTarget> getGenerateTargets();

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        try {

            String sourceDirectory = myMavenProject.getBuild().getSourceDirectory();
            File sourceDirectoryFile = new File(sourceDirectory);
            if (!sourceDirectoryFile.exists()) {
                getLog().info(sourceDirectory + " is not exists");
                return;
            }

            List<File> files = new ArrayList<>(FileUtils.getFiles(sourceDirectoryFile, "**/*.bnf", null));
            files.addAll(externalGrammars);

            if (files.isEmpty()) {
                return;
            }

            String outputDirectory = myMavenProject.getBuild().getDirectory();
            File outputDirectoryFile = new File(outputDirectory, "generated-sources/" + getOutputDirName());
            if (!outputDirectoryFile.exists()) {
                outputDirectoryFile.mkdirs();
            }

            Set<GenerateTarget> generateTargets = getGenerateTargets();

            CacheIO logic = new CacheIO(myMavenProject, "grammar.cache");
            logic.read();

            for (File file : files) {
                if (logic.isUpToDate(file)) {
                    getLog().info("Grammar: " + file.getPath() + " is up to date");
                    continue;
                }

                getLog().info("Generated file: " + file.getPath() + " to " + outputDirectoryFile.getPath());

                runGenerator(file.getPath(), outputDirectoryFile.getPath(), sourceDirectory, getLog(), generateTargets);

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
    private static void runGenerator(String filePath,
                                     String directoryToGenerate,
                                     @Nonnull String sourceDirectory,
                                     Log log,
                                     Set<GenerateTarget> generateTargets) throws Exception {
        try (AutoDisposable rootDisposable = AutoDisposable.newAutoDisposable()) {
            LightApplicationBuilder applicationBuilder = LightApplicationBuilder.create(rootDisposable);

            Application application = applicationBuilder.build();

            LightFileTypeRegistry fileTypeRegistry = (LightFileTypeRegistry) application.getInstance(FileTypeRegistry.class);
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

            new ParserGenerator(file, ioFile.getParentFile().getPath(), directoryToGenerate, "").generate(generateTargets);
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
