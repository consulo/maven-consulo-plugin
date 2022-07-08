package consulo.maven.run;

import org.apache.maven.execution.DefaultMavenExecutionRequest;
import org.apache.maven.execution.DefaultMavenExecutionResult;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.DefaultPlexusContainer;
import org.sonatype.aether.util.DefaultRepositorySystemSession;

import javax.annotation.Nonnull;
import java.io.File;

/**
 * @author VISTALL
 * @since 08-Jul-22
 */
@Mojo(name = "run-desktop-awt-fork", threadSafe = true, requiresDependencyResolution = ResolutionScope.COMPILE_PLUS_RUNTIME, defaultPhase = LifecyclePhase.PACKAGE)
public class RunDesktopAWTForkMojo extends RunForkMojo
{
	@Override
	protected String getMainModuleName(@Nonnull String repositoryChannel)
	{
		return "consulo.desktop.awt.bootstrap";
	}

	@Override
	protected String getMainClassQualifiedName(String repositoryChannel)
	{
		return getMainClassQualifiedNameImpl(repositoryChannel);
	}

	public static String getMainClassQualifiedNameImpl(String repositoryChannel)
	{
		if(VALHALLA_BRANCH.equals(repositoryChannel))
		{
			return RunDesktopAWTMojo.ourMainClassV3;
		}
		return RunDesktopAWTMojo.ourMainClassV2;
	}

	public static void main(String[] args) throws Exception
	{
		MavenProject mavenProject = new MavenProject();
		mavenProject.setFile(new File("W:\\_github.com\\consulo\\consulo-database\\pom.xml"));
		
		RunDesktopAWTForkMojo forkMojo = new RunDesktopAWTForkMojo();
		forkMojo.myRepositoryChannel = VALHALLA_BRANCH;

		forkMojo.myProject = mavenProject;
		forkMojo.mySession = new MavenSession(new DefaultPlexusContainer(), new DefaultRepositorySystemSession(), new DefaultMavenExecutionRequest(), new DefaultMavenExecutionResult());

		forkMojo.execute();
	}
}
