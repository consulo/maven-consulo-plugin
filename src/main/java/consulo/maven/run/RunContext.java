package consulo.maven.run;

import java.io.File;

import org.apache.maven.project.MavenProject;
import consulo.maven.run.util.SystemInfo;

/**
 * @author VISTALL
 * @since 08-Jun-17
 */
public class RunContext
{
	private final File mySandboxDirectory;
	private File myBuildDirectory;

	private String myInnerBuildNumber;

	public RunContext(MavenProject topProject)
	{
		mySandboxDirectory = new File(topProject.getBasedir(), ".consulo/sandbox");
		mySandboxDirectory.mkdirs();

		myBuildDirectory = new File(mySandboxDirectory, "platform");
	}

	public File getLibraryDirectory()
	{
		return new File(getPlatformDirectory(), "lib");
	}

	public File getPlatformDirectory()
	{
		final File platformDir;
		if(SystemInfo.getOS() == SystemInfo.OS.MACOS)
		{
			platformDir = new File(myBuildDirectory, "Consulo.app/Contents/platform");
		}
		else
		{
			platformDir = new File(myBuildDirectory, "Consulo/platform");
		}

		File[] files = platformDir.listFiles();

		if(files == null || files.length != 1)
		{
			throw new IllegalArgumentException();
		}
		return files[0];
	}

	public void setBuildDirectory(File buildDirectory)
	{
		myBuildDirectory = buildDirectory;
	}

	public File getBuildDirectory()
	{
		return myBuildDirectory;
	}

	public File getSandboxDirectory()
	{
		return mySandboxDirectory;
	}

	public void findInnerBuildNumber()
	{
		File platformDirectory = getPlatformDirectory();

		String name = platformDirectory.getName();
		myInnerBuildNumber = name.replace("build", "");
	}

	public String getInnerBuildNumber()
	{
		return myInnerBuildNumber;
	}
}
