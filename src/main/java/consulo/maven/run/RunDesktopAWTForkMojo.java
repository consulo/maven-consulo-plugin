package consulo.maven.run;

import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.ResolutionScope;

import javax.annotation.Nonnull;

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
}
