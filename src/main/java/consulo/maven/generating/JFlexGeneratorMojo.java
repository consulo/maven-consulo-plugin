package consulo.maven.generating;

import JFlex.Main;
import JFlex.Options;
import JFlex.Skeleton;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtil;
import consulo.maven.base.util.cache.CacheLogic;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.FileUtils;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/**
 * @author VISTALL
 * @since 2018-06-19
 */
@Mojo(name = "generate-lexers", threadSafe = true, requiresDependencyResolution = ResolutionScope.NONE)
public class JFlexGeneratorMojo extends AbstractMojo
{
	@Parameter(property = "project", defaultValue = "${project}")
	private MavenProject myMavenProject;

	public JFlexGeneratorMojo()
	{
		Options.no_constructors = true;
		Options.no_backup = true;
		Options.char_at = true;
	}

	@Override
	public void execute() throws MojoExecutionException, MojoFailureException
	{
		try
		{
			List<Pair<File, File>> toGenerateFiles = new ArrayList<>();

			for(String srcDir : myMavenProject.getCompileSourceRoots())
			{
				File srcDirectory = new File(srcDir);

				FileUtil.visitFiles(srcDirectory, file ->
				{
					if("flex".equals(FileUtil.getExtension((CharSequence) file.getName())))
					{
						toGenerateFiles.add(Pair.create(file, srcDirectory));
					}

					return true;
				});
			}

			String outputDirectory = myMavenProject.getBuild().getDirectory();
			File outputDirectoryFile = new File(outputDirectory, "generated-sources/lexers");
			if(outputDirectoryFile.exists())
			{
				FileUtils.deleteDirectory(outputDirectoryFile);
			}

			if(toGenerateFiles.isEmpty())
			{
				return;
			}

			outputDirectoryFile.mkdirs();

			CacheLogic logic = new CacheLogic(myMavenProject, "jflex-generate.cache");

			logic.read();

			myMavenProject.addCompileSourceRoot(outputDirectoryFile.getPath());

			for(Pair<File, File> info : toGenerateFiles)
			{
				File file = info.getFirst();
				File sourceDirectory = info.getSecond();

				if(logic.isUpToDate(file))
				{
					getLog().info("JFlex: " + file.getPath() + " is up to date");
					continue;
				}
				
				String relativePath = FileUtil.getRelativePath(sourceDirectory, file.getParentFile());
				if(relativePath != null)
				{
					File outDirWithPackage = new File(outputDirectoryFile, relativePath);
					outDirWithPackage.mkdirs();
					Options.setDir(outDirWithPackage);
				}

				getLog().info("JFlex: Generated file: " + file.getPath() + " to " + outputDirectoryFile.getPath());

				logic.putCacheEntry(file);

				File skeletonFile = new File(file.getParent(), file.getName() + ".skeleton");
				if(skeletonFile.exists())
				{
					try (InputStream stream = Files.newInputStream(skeletonFile.toPath()))
					{
						Skeleton.readSkelSteam(stream);
					}
					Options.no_constructors = true;
				}
				else
				{
					File marker = new File(file.getParentFile(), file.getName() + ".idea");
					// marker for using old IDEA skeleton or Consulo skeleton
					boolean ideaMarker = marker.exists();
					String name = ideaMarker ? "/META-INF/skeleton/idea-jflex.skeleton" : "/META-INF/skeleton/consulo-jflex.skeleton";
					Skeleton.readSkelSteam(getClass().getResourceAsStream(name));
					Options.no_constructors = !ideaMarker;
				}

				Main.generate(file);
			}

			logic.write();
		}
		catch(Exception e)
		{
			getLog().error(e);
		}
	}
}
