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
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.FileUtils;
import consulo.maven.base.AbstractConsuloMojo;
import consulo.maven.base.util.ExtractUtil;
import consulo.maven.base.util.HubApiUtil;
import consulo.maven.base.util.RepositoryNode;

/**
 * @author VISTALL
 * @since 24-Nov-17
 */
@Mojo(name = "workspace", threadSafe = true, requiresDependencyResolution = ResolutionScope.COMPILE, defaultPhase = LifecyclePhase.PACKAGE)
public class WorkspaceMojo extends AbstractConsuloMojo
{
	public static File getDependenciesDirectory(MavenProject mavenProject)
	{
		String directory = mavenProject.getBuild().getDirectory();

		return new File(directory, "consulo-plugin-dependencies");
	}

	public static File getExtractedDirectory(MavenProject mavenProject)
	{
		String directory = mavenProject.getBuild().getDirectory();

		return new File(directory, "consulo-plugin-extracted");
	}

	@Override
	public void execute() throws MojoExecutionException, MojoFailureException
	{
		if(!myDependencies.isEmpty())
		{
			createDependenciesWorkspace();
		}

		File targetDirectory = getExtractedDirectory(myProject);

		FileUtils.mkdir(targetDirectory.getPath());

		try
		{
			FileUtils.deleteDirectory(targetDirectory);

			FileUtils.mkdir(targetDirectory.getPath());

			Artifact artifact = myProject.getArtifact();
			if(artifact == null)
			{
				throw new MojoFailureException("No project artifact");
			}

			File file = artifact.getFile();
			if(file == null || !file.exists())
			{
				throw new MojoFailureException("Project artifact is not build");
			}

			File libDirectory = new File(targetDirectory, myId + "/lib");

			FileUtils.mkdir(libDirectory.getPath());

			FileUtils.copyFile(file, new File(libDirectory, file.getName()));

			Set<Artifact> dependencyArtifacts = myProject.getDependencyArtifacts();
			for(Artifact dependencyArtifact : dependencyArtifacts)
			{
				String scope = dependencyArtifact.getScope();
				if(Artifact.SCOPE_COMPILE.equals(scope))
				{
					File artifactFile = dependencyArtifact.getFile();
					FileUtils.copyFile(artifactFile, new File(libDirectory, artifactFile.getName()));
				}
			}

			File distDirectory = new File(myProject.getBasedir(), "src/main/dist");
			if(distDirectory.exists())
			{
				FileUtils.copyDirectoryStructure(distDirectory, new File(targetDirectory, myId));
			}
		}
		catch(IOException e)
		{
			throw new MojoFailureException(e.getMessage(), e);
		}
	}

	private void createDependenciesWorkspace() throws MojoFailureException
	{
		File targetDirectory = getDependenciesDirectory(myProject);

		FileUtils.mkdir(targetDirectory.getPath());

		String consuloVersion = PatchPluginXmlMojo.findConsuloVersion(myProject);

		for(String dependencyId : myDependencies)
		{
			getLog().info("Fetching dependency info. Id: '" + dependencyId + "'...");

			RepositoryNode repositoryNode = HubApiUtil.requestRepositoryNodeInfo(myRepositoryChannel, myApiUrl, dependencyId, consuloVersion, null);
			if(repositoryNode == null)
			{
				throw new MojoFailureException("Dependency is not found. Id: " + dependencyId + ", consuloVersion: " + consuloVersion + ", channel: " + myRepositoryChannel);
			}

			try
			{
				File tempFile = File.createTempFile("consulo-plugin", ".zip");
				tempFile.deleteOnExit();

				getLog().info("Downloading dependency: " + dependencyId);

				HubApiUtil.downloadRepositoryNode(myRepositoryChannel, myApiUrl, dependencyId, consuloVersion, null, tempFile);

				File dependencyDirectory = new File(targetDirectory, dependencyId);

				dependencyDirectory.delete();

				getLog().info("Extracting dependency: " + dependencyId);

				ExtractUtil.extractZip(tempFile, targetDirectory);
			}
			catch(Exception e)
			{
				throw new MojoFailureException(e.getMessage(), e);
			}
		}
	}
}
