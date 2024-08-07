package consulo.maven.run;

import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.ResolutionScope;

/**
 * @author VISTALL
 * @since 11-May-22
 */
@Mojo(name = "run-desktop-awt", threadSafe = true, requiresDependencyResolution = ResolutionScope.COMPILE_PLUS_RUNTIME, defaultPhase = LifecyclePhase.PACKAGE)
public class RunDesktopAWTMojo extends RunDesktopMojo {
    public static final String ourMainClassV3 = "consulo.desktop.awt.boot.main.Main";
    public static final String ourMainClassV2 = "consulo.desktop.boot.main.Main";

    @Override
    protected String getMainClassQualifiedName(String repositoryChannel) {
        return getMainClassQualifiedNameImpl(execution.useOldMainClass);
    }

    public static String getMainClassQualifiedNameImpl(boolean useOldMainClass) {
        if (useOldMainClass) {
            return ourMainClassV2;
        }
        return ourMainClassV3;
    }
}
