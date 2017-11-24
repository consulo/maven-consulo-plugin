package consulo.maven.mojo;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

/**
 * @author VISTALL
 * @since 24-Nov-17
 */
public abstract class AbstractConsuloMojo extends AbstractMojo
{
	@Parameter(defaultValue = "${project}", readonly = true)
	protected MavenProject project;

	@Parameter(property = "id", defaultValue = "${project.artifactId}")
	protected String id;
}
