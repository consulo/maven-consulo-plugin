package consulo.maven.base;

import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

import java.util.ArrayList;
import java.util.List;

/**
 * @author VISTALL
 * @since 24-Nov-17
 */
public abstract class AbstractConsuloMojo extends AbstractMojo
{
	public static final String SNAPSHOT = "SNAPSHOT";

	public static final String VALHALLA_BRANCH = "valhalla";

	@Parameter(property = "project", defaultValue = "${project}", readonly = true)
	public MavenProject myProject;

	@Parameter(property = "session", defaultValue = "${session}", readonly = true)
	public MavenSession mySession;

	@Parameter(property = "id", defaultValue = "${project.artifactId}")
	protected String myId;

	@Parameter(alias = "repositoryChannel", defaultValue = "nightly")
	public String myRepositoryChannel = "nightly";

	@Parameter(alias = "apiUrl", defaultValue = "https://hub.consulo.io/api/repository/")
	protected String myApiUrl = "https://hub.consulo.io/api/repository/";

	@Parameter(alias = "dependencies")
	protected List<String> myDependencies = new ArrayList<>();
}
