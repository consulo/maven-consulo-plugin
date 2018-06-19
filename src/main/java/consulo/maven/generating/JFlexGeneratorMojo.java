package consulo.maven.generating;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.FileUtils;
import JFlex.Main;
import JFlex.Options;
import JFlex.Skeleton;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtil;

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

	private static void setSkeleton(boolean idea)
	{
		Skeleton.readSkelResource(idea ? "/skeleton/idea-jflex.skeleton" : "/skeleton/consulo-jflex.skeleton");
		Options.no_constructors = !idea;
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

				List<File> files = FileUtils.getFiles(srcDirectory, "**/*.flex", null);
				if(files.isEmpty())
				{
					continue;
				}

				for(File file : files)
				{
					toGenerateFiles.add(Pair.create(file, srcDirectory));
				}
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

			myMavenProject.addCompileSourceRoot(outputDirectoryFile.getPath());

			for(Pair<File, File> info : toGenerateFiles)
			{
				File file = info.getFirst();
				File sourceDirectory = info.getSecond();

				String relativePath = FileUtil.getRelativePath(sourceDirectory, file.getParentFile());
				if(relativePath != null)
				{
					File outDirWithPackage = new File(outputDirectoryFile, relativePath);
					outDirWithPackage.mkdirs();
					Options.setDir(outDirWithPackage);
				}

				getLog().info("Generated file: " + file.getPath() + " to " + outputDirectoryFile.getPath());

				File marker = new File(file.getParentFile(), file.getName() + ".idea");
				// marker for using old IDEA skeleton or Consulo skeleton
				setSkeleton(marker.exists());

				Main.generate(file);
			}
		}
		catch(Exception e)
		{
			getLog().error(e);
		}
	}
}
