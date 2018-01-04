package consulo.maven.packaging;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Parameter;
import consulo.maven.base.AbstractConsuloMojo;

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

		@Parameter(alias = "copies")
		public List<Copy> copies = new ArrayList<>();
	}

	public static class Copy
	{
		@Parameter
		public String artifact;

		@Parameter
		public String path;
	}

	@Parameter(property = "packaging")
	protected PackagingConfig packaging = new PackagingConfig();

	public static File getAndCheckArtifactFile(Artifact artifact) throws MojoFailureException
	{
		File artifactFile = artifact.getFile();
		if(artifactFile == null || !artifactFile.exists())
		{
			throw new MojoFailureException("Artifact " + artifact.getGroupId() + ":" + artifact.getArtifactId() + " is not build");
		}
		return artifactFile;
	}

	protected static boolean isValidArtifactForPackaging(Artifact artifact)
	{
		return Artifact.SCOPE_COMPILE.equals(artifact.getScope()) || Artifact.SCOPE_RUNTIME.equals(artifact.getScope());
	}
}
