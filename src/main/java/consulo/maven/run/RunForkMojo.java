package consulo.maven.run;

import consulo.maven.base.util.SystemInfo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;

import javax.annotation.Nonnegative;
import javax.annotation.Nonnull;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * @author VISTALL
 * @since 08-Jul-22
 */
public abstract class RunForkMojo extends RunMojo
{
	@Override
	protected void run(String mainClassQualifiedName, RunContext context) throws MojoExecutionException, MojoFailureException
	{
		Map<String, String> systemProperties = getSystemProperties(context);

		String javaHome = System.getProperty("java.home");

		File bootDirectory = new File(context.getPlatformDirectory(), "boot");

		String executablePath = SystemInfo.getOS() == SystemInfo.OS.WINDOWS ? "bin/java.exe" : "bin/java";

		List<String> args = new ArrayList<>();
		args.add(new File(javaHome, executablePath).getAbsolutePath());
		args.add("-p");
		args.add(bootDirectory.getAbsolutePath());

		try
		{
			List<String> appOptions = Files.readAllLines(context.getPlatformDirectory().toPath().resolve("bin/app.vmoptions"));
			for(String option : appOptions)
			{
				args.add(option);
			}
		}
		catch(IOException e)
		{
			throw new MojoFailureException(e.getMessage());
		}

		for(Map.Entry<String, String> entry : systemProperties.entrySet())
		{
			args.add("-D" + entry.getKey() + "=" + entry.getValue());
		}

		args.add("-m");
		args.add(getMainModuleName(myRepositoryChannel) + "/" + mainClassQualifiedName);

		ProcessBuilder builder = new ProcessBuilder(args);
		builder.inheritIO();

		try
		{
			Process process = builder.start();
			int exitCode = process.waitFor();
			if(exitCode != 0)
			{
				throw new MojoFailureException("Consulo exit with code " + exitCode);
			}
		}
		catch(IOException | InterruptedException e)
		{
			throw new MojoFailureException(e.getMessage());
		}
	}

	@Nonnegative
	protected abstract String getMainModuleName(@Nonnull String repositoryChannel);
}
