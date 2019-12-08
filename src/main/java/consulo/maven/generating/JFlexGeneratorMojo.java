package consulo.maven.generating;

import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtil;
import consulo.maven.base.util.cache.CacheIO;
import jflex.Main;
import jflex.Options;
import jflex.Skeleton;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
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
		Options.no_constructor = true;
		Options.no_backup = true;
		// integrated by default Options.char_at = true;
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

			if(toGenerateFiles.isEmpty())
			{
				return;
			}

			String outputDirectory = myMavenProject.getBuild().getDirectory();
			File outputDirectoryFile = new File(outputDirectory, "generated-sources/lexers");

			outputDirectoryFile.mkdirs();

			CacheIO logic = new CacheIO(myMavenProject, "jflex-generate.cache");

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
					try (BufferedReader stream = new BufferedReader(new InputStreamReader(Files.newInputStream(skeletonFile.toPath()), StandardCharsets.UTF_8)))
					{
						Skeleton.readSkel(stream);
					}
					Options.no_constructor = true;
				}
				else
				{
					File marker = new File(file.getParentFile(), file.getName() + ".idea");
					// marker for using old IDEA skeleton or Consulo skeleton
					boolean ideaMarker = marker.exists();
					String name = ideaMarker ? "/META-INF/skeleton/idea-jflex.skeleton" : "/META-INF/skeleton/consulo-jflex.skeleton";
					try (BufferedReader stream = new BufferedReader(new InputStreamReader(getClass().getResourceAsStream(name), StandardCharsets.UTF_8)))
					{
						Skeleton.readSkel(stream);
					}
					Options.no_constructor = !ideaMarker;
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
