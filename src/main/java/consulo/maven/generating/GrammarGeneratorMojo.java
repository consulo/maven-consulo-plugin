package consulo.maven.generating;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.List;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.FileUtils;
import org.intellij.grammar.BnfFileType;
import org.intellij.grammar.BnfParserDefinition;
import org.intellij.grammar.generator.ParserGenerator;
import org.intellij.grammar.java.JavaHelper;
import org.intellij.grammar.psi.BnfFile;
import com.intellij.codeInsight.FileModificationService;
import com.intellij.core.CoreApplicationEnvironment;
import com.intellij.core.CoreProjectEnvironment;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.local.CoreLocalFileSystem;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.impl.search.PsiSearchHelperImpl;
import com.intellij.psi.search.PsiSearchHelper;
import consulo.annotations.RequiredReadAction;
import consulo.psi.tree.ASTCompositeFactory;
import consulo.psi.tree.ASTLazyFactory;
import consulo.psi.tree.ASTLeafFactory;
import consulo.psi.tree.PsiElementFactory;
import consulo.psi.tree.impl.DefaultASTCompositeFactory;
import consulo.psi.tree.impl.DefaultASTLazyFactory;
import consulo.psi.tree.impl.DefaultASTLeafFactory;
import consulo.psi.tree.impl.DefaultPsiElementFactory;

/**
 * @author VISTALL
 * @since 2018-06-18
 */
//@Mojo(name = "generate-parsers", threadSafe = true, requiresDependencyResolution = ResolutionScope.NONE, defaultPhase = LifecyclePhase.GENERATE_SOURCES)
public class GrammarGeneratorMojo extends AbstractMojo
{
	@Parameter(property = "project", defaultValue = "${project}")
	private MavenProject myMavenProject;

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
		}
		catch(Exception e)
		{
			getLog().error(e);
		}
	}

	@RequiredReadAction
	private static void runGenerator(String filePath, String directoryToGenerate, @Nonnull String sourceDirectory) throws Exception
	{
		Disposable rootDisposable = Disposer.newDisposable();
		CoreApplicationEnvironment applicationEnvironment = new CoreApplicationEnvironment(rootDisposable)
		{
			@Nonnull
			@Override
			protected CoreLocalFileSystem createLocalFileSystem()
			{
				return new CoreLocalFileSystem()
				{
					@Nonnull
					@Override
					public VirtualFile createChildFile(Object requestor, @Nonnull VirtualFile vDir, @Nonnull String fileName) throws IOException
					{
						File temp = new File(vDir.getPath(), fileName);
						temp.createNewFile();
						return findFileByIoFile(temp);
					}
				};
			}
		};

//		Extensions.getRootArea().registerExtensionPoint("com.intellij.lang.substitutor", LanguageExtensionPoint.class.getName());
//		Extensions.getRootArea().registerExtensionPoint("com.intellij.lang.fileViewProviderFactory", KeyedLazyInstanceEP.class.getName());
//		Extensions.getRootArea().registerExtensionPoint("com.intellij.lang.parserDefinition", KeyedLazyInstanceEP.class.getName());
//		Extensions.getRootArea().registerExtensionPoint("com.intellij.lang.versionResolver", LanguageExtensionPoint.class.getName());
//		Extensions.getRootArea().registerExtensionPoint("com.intellij.lang.defineVersion", LanguageExtensionPoint.class.getName());
//
//		Extensions.getRootArea().registerExtensionPoint("com.intellij.lang.psi.elementFactory", PsiElementFactory.class.getName());
//		Extensions.getRootArea().registerExtensionPoint("com.intellij.referencesSearch", QueryExecutor.class.getName());

		applicationEnvironment.registerParserDefinition(new BnfParserDefinition());
		applicationEnvironment.registerApplicationService(FileModificationService.class, new FileModificationService()
		{
			@Override
			public boolean preparePsiElementsForWrite(@Nonnull Collection<? extends PsiElement> elements)
			{
				return true;
			}

			@Override
			public boolean prepareFileForWrite(@Nullable PsiFile psiFile)
			{
				return true;
			}

			@Override
			public boolean prepareVirtualFilesForWrite(@Nonnull Project project, @Nonnull Collection<VirtualFile> files)
			{
				return true;
			}
		});

		applicationEnvironment.addExtension(ASTLazyFactory.EP.getExtensionPointName(), new DefaultASTLazyFactory());
		applicationEnvironment.addExtension(ASTCompositeFactory.EP.getExtensionPointName(), new DefaultASTCompositeFactory());
		applicationEnvironment.addExtension(ASTLeafFactory.EP.getExtensionPointName(), new DefaultASTLeafFactory());
		applicationEnvironment.addExtension(PsiElementFactory.EP.getExtensionPointName(), new DefaultPsiElementFactory());

		applicationEnvironment.registerFileType(BnfFileType.INSTANCE, BnfFileType.INSTANCE.getDefaultExtension());

		CoreProjectEnvironment projectEnvironment = new CoreProjectEnvironment(rootDisposable, applicationEnvironment)
		{
			@Override
			protected void preregisterServices()
			{
				myProject.registerService(PsiSearchHelper.class, PsiSearchHelperImpl.class);
				myProject.registerService(JavaHelper.class, new JavaParserJavaHelper(sourceDirectory, directoryToGenerate));
			}
		};

		Project project = projectEnvironment.getProject();
		PsiManager psiManager = PsiManager.getInstance(project);

		VirtualFile fileByIoFile = applicationEnvironment.getLocalFileSystem().findFileByIoFile(new File(filePath));
		if(fileByIoFile == null)
		{
			System.out.println("File not exists: " + filePath);
			System.exit(-1);
			return;
		}

		BnfFile file = (BnfFile) psiManager.findFile(fileByIoFile);

		new ParserGenerator(file, fileByIoFile.getParent().getPath(), directoryToGenerate).generate();
	}
}
