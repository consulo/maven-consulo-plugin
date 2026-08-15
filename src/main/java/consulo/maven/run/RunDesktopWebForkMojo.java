package consulo.maven.run;

import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.ResolutionScope;

import javax.annotation.Nonnull;

/**
 * @author VISTALL
 * @since 2026-08-15
 */
@Mojo(name = "run-desktop-web-fork", threadSafe = true, requiresDependencyResolution = ResolutionScope.COMPILE_PLUS_RUNTIME, defaultPhase = LifecyclePhase.PACKAGE)
public class RunDesktopWebForkMojo extends RunForkMojo {
    @Override
    protected String getMainModuleName(@Nonnull String repositoryChannel) {
        return "consulo.web.bootstrap";
    }

    @Override
    protected String getPlatformId() {
        return "consulo.dist.web";
    }

    @Override
    protected String getMainClassQualifiedName(String repositoryChannel) {
        return "consulo.web.boot.main.Main";
    }
}
