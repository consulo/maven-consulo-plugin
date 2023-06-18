package consulo.maven.run;

import consulo.maven.base.AbstractConsuloMojo;
import consulo.maven.base.util.ExtractUtil;
import consulo.maven.base.util.HubApiUtil;
import consulo.maven.base.util.RepositoryNode;
import consulo.maven.base.util.SystemInfo;
import consulo.maven.packaging.WorkspaceMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Parameter;
import org.codehaus.plexus.util.FileUtils;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * @author VISTALL
 * @since 08-Jul-22
 */
public abstract class RunMojo extends AbstractConsuloMojo
{
	public static class ExecutionConfig
	{
		@Parameter(property = "buildNumber", defaultValue = SNAPSHOT)
		public String buildNumber = SNAPSHOT;

		@Parameter(property = "buildDirectory", defaultValue = "")
		public String buildDirectory;

		@Parameter(property = "useDefaultWorkspaceDirectory", defaultValue = "true")
		public boolean useDefaultWorkspaceDirectory = true;

		@Parameter(property = "useOldMainClass", defaultValue = "false")
		public boolean useOldMainClass = false;

		@Parameter(property = "pluginDirectories")
		public List<String> pluginDirectories = new ArrayList<>();

		@Parameter(property = "arguments")
		public String[] arguments = new String[0];
	}

	@Override
	public void execute() throws MojoExecutionException, MojoFailureException
	{
		if(execution.arguments == null)
		{
			execution.arguments = new String[0];
		}

		RunContext context = new RunContext(myProject);

		if(!validateBuild(context))
		{
			return;
		}

		context.findInnerBuildNumber();

		String mainClassQualifiedName = getMainClassQualifiedName(myRepositoryChannel);

		run(mainClassQualifiedName, context);
	}

	@Parameter(property = "execution")
	protected ExecutionConfig execution = new ExecutionConfig();

	protected abstract void run(String mainClassQualifiedName, RunContext context) throws MojoExecutionException, MojoFailureException;

	protected Map<String, String> getSystemProperties(RunContext context) throws MojoFailureException
	{
		Map<String, String> map = new HashMap<>();
		map.put("idea.home.path", context.getPlatformDirectory().getPath());
		map.put("consulo.in.sandbox", "true");  // sandbox mode
		map.put("consulo.maven.console.log", "true"); // redirect file log to console
		map.put("consulo.config.path", context.getSandboxDirectory().getPath() + "/config");
		map.put("consulo.system.path", context.getSandboxDirectory().getPath() + "/system");

		// deprecated option
		map.put("idea.is.internal", "true");
		map.put("idea.config.path", context.getSandboxDirectory().getPath() + "/config");
		map.put("idea.system.path", context.getSandboxDirectory().getPath() + "/system");
		List<String> pluginPaths = new ArrayList<>();

		if(execution.useDefaultWorkspaceDirectory)
		{
			File targetDirectory = WorkspaceMojo.getExtractedDirectory(myProject);

			if(targetDirectory.exists())
			{
				pluginPaths.add(targetDirectory.getPath());
			}
		}

		if(!myDependencies.isEmpty())
		{
			File dependenciesDirectory = WorkspaceMojo.getDependenciesDirectory(myProject);
			pluginPaths.add(dependenciesDirectory.getPath());
		}

		for(String pluginDirectory : execution.pluginDirectories)
		{
			File dir = new File(pluginDirectory);
			if(dir.exists())
			{
				try
				{
					pluginPaths.add(dir.getCanonicalPath());
				}
				catch(IOException e)
				{
					throw new MojoFailureException(e.getMessage(), e);
				}
			}
		}

		pluginPaths.add(context.getSandboxDirectory().getPath() + "/config/plugins");

		map.put("consulo.plugins.paths", String.join(File.pathSeparator, pluginPaths));
		map.put("consulo.install.plugins.path", context.getSandboxDirectory().getPath() + "/config/plugins");
		return map;
	}

	private boolean validateBuild(RunContext context) throws MojoExecutionException, MojoFailureException
	{
		if(execution.buildDirectory != null)
		{
			context.setBuildDirectory(new File(execution.buildDirectory));
			return true;
		}

		File platformDirectory = context.getBuildDirectory();
		File buildNumberFile = new File(platformDirectory, "build.txt");

		String oldBuildNumber = null;
		if(platformDirectory.exists())
		{
			if(buildNumberFile.exists())
			{
				try
				{
					oldBuildNumber = FileUtils.fileRead(buildNumberFile);
				}
				catch(IOException ignored)
				{
				}
			}
		}

		if(execution.buildNumber != null && execution.buildNumber.equals(oldBuildNumber))
		{
			getLog().info("Consulo Build: " + execution.buildNumber + " - ok");
			return true;
		}

		if(mySession.isOffline())
		{
			if(oldBuildNumber == null)
			{
				getLog().error("No connection and no old Consulo build.");
				return false;
			}
			else
			{
				getLog().info("No connection - using old Consulo build");
				return true;
			}
		}
		else
		{
			getLog().info("Fetching platform info...");
			RepositoryNode repositoryNode = HubApiUtil.requestRepositoryNodeInfo(myRepositoryChannel, myApiUrl, SystemInfo.getOS().getPlatformId(), execution.buildNumber, null, getLog());
			if(repositoryNode == null)
			{
				if(oldBuildNumber == null)
				{
					getLog().error("No connection and no old Consulo build.");
					return false;
				}
				else
				{
					getLog().info("No connection - using old Consulo build");
					return true;
				}
			}
			else
			{
				if(Objects.equals(repositoryNode.version, oldBuildNumber))
				{
					getLog().info("Consulo build is not changed: " + oldBuildNumber);
					return true;
				}
				else if(oldBuildNumber != null)
				{
					getLog().info("Consulo build is changed. Old build: " + oldBuildNumber + ", downloading new build: " + repositoryNode.version);
				}
				else
				{
					getLog().info("No old build, downloading Consulo build: " + repositoryNode.version);
				}

				try
				{
					File tempFile = File.createTempFile("consulo_build", "tar.gz");
					tempFile.deleteOnExit();

					HubApiUtil.downloadRepositoryNode(myRepositoryChannel, myApiUrl, SystemInfo.getOS().getPlatformId(), execution.buildNumber, null, tempFile, getLog());

					if(oldBuildNumber != null)
					{
						getLog().info("Deleting old build");
					}

					FileUtils.deleteDirectory(platformDirectory);

					getLog().info("Extracting new build");

					ExtractUtil.extractTarGz(tempFile, platformDirectory);

					FileUtils.fileWrite(buildNumberFile.getPath(), repositoryNode.version);

					return true;
				}
				catch(Exception e)
				{
					throw new MojoExecutionException(e.getMessage(), e);
				}
			}
		}
	}

	protected String getMainClassQualifiedName(String repositoryChannel)
	{
		return RunDesktopAWTMojo.getMainClassQualifiedNameImpl(execution.useOldMainClass);
	}
}
