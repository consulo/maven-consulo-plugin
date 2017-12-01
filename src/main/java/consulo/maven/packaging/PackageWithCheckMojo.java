package consulo.maven.packaging;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.ResolutionScope;

/**
 * @author VISTALL
 * @since 02-Dec-17
 */
@Mojo(name = "packageWithCheck", threadSafe = true, requiresDependencyResolution = ResolutionScope.COMPILE)
public class PackageWithCheckMojo extends PackageMojo
{
	@Override
	public void execute() throws MojoExecutionException, MojoFailureException
	{
		if(packaging.skip)
		{
			getLog().info("Packaging - disabled");
			return;
		}

		super.execute();
	}

	@Override
	protected void patchPluginXml() throws MojoExecutionException, MojoFailureException
	{
		// nothing
	}
}
