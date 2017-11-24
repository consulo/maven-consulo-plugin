package consulo.maven.packaging;

import org.apache.maven.plugins.annotations.Parameter;
import consulo.maven.mojo.AbstractConsuloMojo;

/**
 * @author VISTALL
 * @since 24-Nov-17
 */
public abstract class AbstractPackagingMojo extends AbstractConsuloMojo
{
	public static class PackagingConfig
	{
		@Parameter(property = "skip", defaultValue = "false")
		public boolean skip = false;

		@Parameter(property = "version", defaultValue = "SNAPSHOT")
		public String version = "SNAPSHOT";
	}

	@Parameter(property = "packaging")
	protected PackagingConfig packaging = new PackagingConfig();
}
