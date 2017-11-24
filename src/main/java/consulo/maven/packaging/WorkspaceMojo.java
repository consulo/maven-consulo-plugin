package consulo.maven.packaging;

import java.io.File;
import java.io.IOException;
import java.util.Set;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.codehaus.plexus.util.FileUtils;

/**
 * @author VISTALL
 * @since 24-Nov-17
 */
@Mojo(name = "workspace", threadSafe = true, requiresDependencyResolution = ResolutionScope.COMPILE, defaultPhase = LifecyclePhase.PACKAGE)
public class WorkspaceMojo extends AbstractPackagingMojo
{
	@Override
	public void execute() throws MojoExecutionException, MojoFailureException
	{
		String directory = project.getBuild().getDirectory();

		File targetDirectory = new File(directory, "workspace");

		FileUtils.mkdir(directory);

		try
		{
			FileUtils.deleteDirectory(targetDirectory);

			FileUtils.mkdir(targetDirectory.getPath());

			Artifact artifact = project.getArtifact();
			if(artifact == null)
			{
				throw new MojoFailureException("No project artifact");
			}

			File file = artifact.getFile();
			if(!file.exists())
			{
				throw new MojoFailureException("Project artifact is not build");
			}

			File libDirectory = new File(targetDirectory, id + "/lib");

			FileUtils.mkdir(libDirectory.getPath());

			FileUtils.copyFile(file, new File(libDirectory, file.getName()));

			Set<Artifact> dependencyArtifacts = project.getDependencyArtifacts();
			for(Artifact dependencyArtifact : dependencyArtifacts)
			{
				String scope = dependencyArtifact.getScope();
				if(Artifact.SCOPE_COMPILE.equals(scope))
				{
					File artifactFile = dependencyArtifact.getFile();
					FileUtils.copyFile(artifactFile, new File(libDirectory, artifactFile.getName()));
				}
			}
		}
		catch(IOException e)
		{
			throw new MojoFailureException(e.getMessage(), e);
		}
	}

}
