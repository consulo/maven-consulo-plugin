package consulo.maven.packaging;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.DefaultArtifact;
import org.apache.maven.artifact.handler.DefaultArtifactHandler;
import org.apache.maven.artifact.resolver.ArtifactResolutionRequest;
import org.apache.maven.artifact.resolver.ArtifactResolutionResult;
import org.apache.maven.artifact.resolver.ArtifactResolver;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Parameter;
import org.codehaus.plexus.util.StringUtils;
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

	@Parameter(defaultValue = "${session}")
	protected MavenSession mySession;

	@Component(role = ArtifactResolver.class)
	protected ArtifactResolver artifactResolver;

	public static File getAndCheckArtifactFile(Artifact artifact) throws MojoFailureException
	{
		File artifactFile = artifact.getFile();
		if(artifactFile == null || !artifactFile.exists())
		{
			throw new MojoFailureException("Artifact " + artifact.getGroupId() + ":" + artifact.getArtifactId() + " is not build");
		}
		return artifactFile;
	}

	public Artifact resolveArtifact(String coords) throws MojoFailureException
	{
		int i = StringUtils.countMatches(coords, ":");
		if(i > 1)
		{
			Pattern p = Pattern.compile("([^: ]+):([^: ]+)(:([^: ]*)(:([^: ]+))?)?:([^: ]+)");
			Matcher m = p.matcher(coords);
			if(!m.matches())
			{
				throw new IllegalArgumentException("Bad artifact coordinates" + ", expected format is <groupId>:<artifactId>[:<extension>[:<classifier>]]:<version>");
			}
			String groupId = m.group(1);
			String artifactId = m.group(2);
			String extension = get(m.group(4), "jar");
			String classifier = get(m.group(6), "");
			String version = m.group(7);

			DefaultArtifact target = new DefaultArtifact(groupId, artifactId, version, Artifact.SCOPE_COMPILE, extension, classifier, new DefaultArtifactHandler());

			ArtifactResolutionRequest request = new ArtifactResolutionRequest();
			request.setRemoteRepositories(myProject.getRemoteArtifactRepositories());
			request.setLocalRepository(mySession.getLocalRepository());
			request.setArtifact(target);

			ArtifactResolutionResult artifactResult = artifactResolver.resolve(request);

			Set<Artifact> artifacts = artifactResult.getArtifacts();
			for(Artifact artifact : artifacts)
			{
				return artifact;
			}
		}
		else
		{
			Set<Artifact> dependencyArtifacts = myProject.getDependencyArtifacts();
			for(Artifact dependencyArtifact : dependencyArtifacts)
			{
				String actual = dependencyArtifact.getGroupId() + ":" + dependencyArtifact.getArtifactId();

				if(actual.equals(coords))
				{
					getAndCheckArtifactFile(dependencyArtifact);

					return dependencyArtifact;
				}
			}
		}

		throw new MojoFailureException("Artifact " + coords + " is not found");
	}

	private static String get(String value, String defaultValue)
	{
		return (value == null || value.length() <= 0) ? defaultValue : value;
	}

	protected static String getRelativePathForCopy(Copy copy, File file)
	{
		if(copy.path.endsWith("/"))
		{
			return copy.path + file.getName();
		}
		return copy.path;
	}

	protected static boolean isValidArtifactForPackaging(Artifact artifact)
	{
		return Artifact.SCOPE_COMPILE.equals(artifact.getScope()) || Artifact.SCOPE_RUNTIME.equals(artifact.getScope());
	}
}
