package consulo.maven.generating;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.intellij.codeInsight.ContainerProvider;
import com.intellij.lang.LanguageBraceMatching;
import com.intellij.lang.LanguageExtensionPoint;
import com.intellij.lang.LanguageParserDefinitions;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.mock.MockDumbService;
import com.intellij.mock.MockReferenceProvidersRegistry;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.TransactionId;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.extensions.impl.ExtensionsAreaImpl;
import com.intellij.openapi.fileTypes.FileTypeExtensionPoint;
import com.intellij.openapi.fileTypes.FileTypeRegistry;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.StandardFileSystems;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.VirtualFileSystem;
import com.intellij.openapi.vfs.impl.VirtualFileManagerImpl;
import com.intellij.psi.*;
import com.intellij.psi.impl.DocumentCommitProcessor;
import com.intellij.psi.impl.PsiCachedValuesFactory;
import com.intellij.psi.impl.search.CachesBasedRefSearcher;
import com.intellij.psi.impl.search.PsiSearchHelperImpl;
import com.intellij.psi.impl.source.resolve.ResolveCache;
import com.intellij.psi.impl.source.resolve.reference.ReferenceProvidersRegistry;
import com.intellij.psi.search.PsiSearchHelper;
import com.intellij.psi.search.UseScopeEnlarger;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.util.CachedValuesFactory;
import com.intellij.util.CachedValuesManagerImpl;
import com.intellij.util.QueryExecutor;
import consulo.annotation.access.RequiredReadAction;
import consulo.disposer.Disposable;
import consulo.injecting.InjectingContainerBuilder;
import consulo.maven.generating.consuloApi.InjectedLanguageManagerStub;
import consulo.maven.generating.consuloApi.LocalFileSystemStub;
import consulo.maven.generating.consuloApi.PsiDocumentManagerStub;
import consulo.psi.tree.PsiElementFactory;
import consulo.psi.tree.impl.DefaultPsiElementFactory;
import consulo.test.light.LightApplicationBuilder;
import consulo.test.light.LightProjectBuilder;
import consulo.test.light.impl.LightFileTypeRegistry;
import org.apache.maven.model.Build;
import org.apache.maven.model.Resource;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.logging.SystemStreamLog;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.FileUtils;
import org.intellij.grammar.BnfFileType;
import org.intellij.grammar.BnfLanguage;
import org.intellij.grammar.BnfParserDefinition;
import org.intellij.grammar.generator.Case;
import org.intellij.grammar.generator.ParserGenerator;
import org.intellij.grammar.generator.ParserGeneratorUtil;
import org.intellij.grammar.java.JavaHelper;
import org.intellij.grammar.psi.BnfFile;
import org.intellij.grammar.psi.BnfRule;
import org.jetbrains.annotations.NonNls;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
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
public class GrammarGeneratorMojo extends AbstractMojo
{
	@Parameter(property = "project", defaultValue = "${project}")
	private MavenProject myMavenProject;

	public static void main(String[] args) throws Exception
	{
		MavenProject mavenProject = new MavenProject();

		File projectDir = new File("W:\\_github.com\\consulo\\consulo-google-go\\go-impl");
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
	public void execute() throws MojoExecutionException, MojoFailureException
	{
		try
		{
			String sourceDirectory = myMavenProject.getBuild().getSourceDirectory();
			File sourceDirectoryFile = new File(sourceDirectory);
			if(!sourceDirectoryFile.exists())
			{
				getLog().info(sourceDirectory + " is not exists");
				return;
			}

			List<File> files = FileUtils.getFiles(sourceDirectoryFile, "**/*.bnf", null);
			if(files.isEmpty())
			{
				return;
			}

			String outputDirectory = myMavenProject.getBuild().getDirectory();
			File outputDirectoryFile = new File(outputDirectory, "generated-sources/parsers");
			if(!outputDirectoryFile.exists())
			{
				outputDirectoryFile.mkdirs();
			}

			for(File file : files)
			{
				System.out.println("Generated file: " + file.getPath() + " to " + outputDirectoryFile.getPath());

				runGenerator(file.getPath(), outputDirectoryFile.getPath(), sourceDirectory);
			}

			myMavenProject.addCompileSourceRoot(outputDirectoryFile.getPath());
		}
		catch(Exception e)
		{
			getLog().error(e);
		}
	}

	@RequiredReadAction
	private static void runGenerator(String filePath, String directoryToGenerate, @Nonnull String sourceDirectory) throws Exception
	{
		Disposable rootDisposable = Disposable.newDisposable();
		LightApplicationBuilder builder = LightApplicationBuilder.create(rootDisposable, new LightApplicationBuilder.DefaultRegistrator()
		{
			@Override
			public void registerServices(@Nonnull InjectingContainerBuilder builder)
			{
				super.registerServices(builder);

				builder.bind(PsiReferenceService.class).to(PsiReferenceServiceImpl.class);
				builder.bind(VirtualFileManager.class).to(VirtualFileManagerImpl.class);
				builder.bind(ReferenceProvidersRegistry.class).to(MockReferenceProvidersRegistry.class);
			}

			@Override
			public void registerExtensionPointsAndExtensions(@Nonnull ExtensionsAreaImpl area)
			{
				super.registerExtensionPointsAndExtensions(area);

				registerExtensionPoint(area, VirtualFileSystem.EP_NAME, VirtualFileSystem.class);

				registerExtension(area, VirtualFileSystem.EP_NAME, new LocalFileSystemStub());

				registerExtensionPoint(area, ExtensionPointName.create("com.intellij.fileType.fileViewProviderFactory"), FileTypeExtensionPoint.class);

				LanguageExtensionPoint languageExtensionPoint = new LanguageExtensionPoint<>();
				languageExtensionPoint.language = BnfLanguage.INSTANCE.getID();
				languageExtensionPoint.implementationClass = BnfParserDefinition.class.getName();

				ExtensionPointName ep = LanguageParserDefinitions.INSTANCE.getExtensionPointName();
				registerExtension(area, ep, languageExtensionPoint);

				registerExtensionPoint(area, LanguageBraceMatching.INSTANCE.getExtensionPointName(), LanguageExtensionPoint.class);

				registerExtensionPoint(area, PsiElementFactory.EP.getExtensionPointName(), PsiElementFactory.class);

				registerExtension(area, PsiElementFactory.EP.getExtensionPointName(), new DefaultPsiElementFactory());

				registerExtensionPoint(area, UseScopeEnlarger.EP_NAME, UseScopeEnlarger.class);
				registerExtensionPoint(area, ContainerProvider.EP_NAME, ContainerProvider.class);

				ExtensionPointName<Object> ref = ExtensionPointName.create("com.intellij.referencesSearch");
				registerExtensionPoint(area, ref, QueryExecutor.class);
				registerExtension(area, ref, new CachesBasedRefSearcher());

				//				registerExtensionPoint(area, FileTypeFactory.FILE_TYPE_FACTORY_EP, FileTypeFactory.class);
				//
				//				registerExtension(area, FileTypeFactory.FILE_TYPE_FACTORY_EP, new FileTypeFactory()
				//				{
				//					@Override
				//					public void createFileTypes(@Nonnull FileTypeConsumer fileTypeConsumer)
				//					{
				//						fileTypeConsumer.consume(BnfFileType.INSTANCE);
				//					}
				//				});
			}
		});
		Application application = builder.build();

		LightFileTypeRegistry registry = (LightFileTypeRegistry) FileTypeRegistry.getInstance();
		registry.registerFileType(BnfFileType.INSTANCE, "bnf");

		LightProjectBuilder projectBuilder = LightProjectBuilder.create(application, new LightProjectBuilder.DefaultRegistrator()
		{
			@Override
			public void registerServices(@Nonnull InjectingContainerBuilder builder)
			{
				super.registerServices(builder);

				builder.bind(PsiDocumentManager.class).to(PsiDocumentManagerStub.class);
				builder.bind(ResolveCache.class).to(ResolveCache.class);
				builder.bind(PsiSearchHelper.class).to(PsiSearchHelperImpl.class);
				builder.bind(DumbService.class).to(MockDumbService.class);
				builder.bind(DocumentCommitProcessor.class).to(new DocumentCommitProcessor()
				{
					@Override
					public void commitSynchronously(@Nonnull Document document, @Nonnull Project project, @Nonnull PsiFile psiFile)
					{
					}

					@Override
					public void commitAsynchronously(@Nonnull Project project, @Nonnull Document document, @Nonnull @NonNls Object reason, @Nullable TransactionId context)
					{
					}
				});
				builder.bind(JavaHelper.class).to(new JavaParserJavaHelper(sourceDirectory, directoryToGenerate));

				builder.bind(CachedValuesManager.class).to(CachedValuesManagerImpl.class);

				builder.bind(CachedValuesFactory.class).to(PsiCachedValuesFactory.class);

				builder.bind(InjectedLanguageManager.class).to(InjectedLanguageManagerStub.class);
			}
		});

		Project project = projectBuilder.build();
		PsiManager psiManager = PsiManager.getInstance(project);

		File ioFile = new File(filePath);
		VirtualFile fileByIoFile = StandardFileSystems.local().findFileByPath(ioFile.getPath());
		if(fileByIoFile == null)
		{
			System.out.println("File not exists: " + filePath);
			System.exit(-1);
			return;
		}

		BnfFile file = (BnfFile) psiManager.findFile(fileByIoFile);

		List<BnfRule> rules = file.getRules();

		BiMap<String, String> names = HashBiMap.create();
		Map<String, String> baseClassNames = new HashMap<>();
		for(BnfRule rule : rules)
		{

			ParserGeneratorUtil.NameFormat prefix = ParserGeneratorUtil.getPsiClassFormat(file);
			ParserGeneratorUtil.NameFormat prefixImpl = ParserGeneratorUtil.getPsiImplClassFormat(file);

			String qualifiedRuleClassName = ParserGeneratorUtil.getQualifiedRuleClassName(rule, false);
			names.put(ParserGeneratorUtil.toIdentifier(rule.getName(), prefix, Case.CAMEL), qualifiedRuleClassName);
			names.put(ParserGeneratorUtil.toIdentifier(rule.getName(), prefixImpl, Case.CAMEL), ParserGeneratorUtil.getQualifiedRuleClassName(rule, true));

			List<String> ruleClasses = new ArrayList<>(ParserGeneratorUtil.getRuleClasses(rule));

			String baseClass = ruleClasses.get(3);

			baseClassNames.put(qualifiedRuleClassName, baseClass);
		}

		JavaParserJavaHelper helper = (JavaParserJavaHelper) JavaHelper.getJavaHelper(file);

		helper.setRuleClassNames(names);
		helper.setBaseClassNames(baseClassNames);

		new ParserGenerator(file, ioFile.getParentFile().getPath(), directoryToGenerate).generate();
	}
}
