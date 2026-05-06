package consulo.maven.packaging;

import org.apache.maven.model.Build;
import org.apache.maven.project.MavenProject;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.openjdk.jmh.Main;
import org.openjdk.jmh.annotations.*;

import java.io.File;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Disabled("Performance measurement. It takes about a minute to run. Run it manually")
@State(Scope.Benchmark)
@Threads(1)
public class BuildIndexMojoBenchmarkTest {
    BuildIndexMojo mojo;

    @Setup(Level.Invocation)
    @SuppressWarnings("unused")
    public void setupInvocation() throws Exception {
        File pluginRoot = getSourcePluginRoot();
        System.out.println(pluginRoot);

        File workDir = pluginRoot.getParentFile();

        Build build = new Build();
        build.setOutputDirectory(workDir.getAbsolutePath());
        build.setDirectory(workDir.getAbsolutePath());

        MavenProject mavenProject;
        mavenProject = new MavenProject();
        mavenProject.setBuild(build);

        mojo = new BuildIndexMojo();
        mojo.myProject = mavenProject;
        mojo.myPluginRoots = List.of(pluginRoot);
    }

    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    @Fork(1)
    @Warmup(iterations = 50, time = 1, timeUnit = TimeUnit.MILLISECONDS)
    @Measurement(iterations = 100, time = 1, timeUnit = TimeUnit.MILLISECONDS)
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    @SuppressWarnings("unused")
    public void test() throws Exception {
        mojo.execute();
    }

    @Test
    public void benchmark() throws Exception {
        Main.main(new String[0]);
    }

    private File getSourcePluginRoot() throws URISyntaxException {
        URL resourceUrl = getClass().getClassLoader().getResource("pluginRoot");
        if (resourceUrl == null) {
            throw new IllegalStateException("No pluginRoot folder found in src/test/resources");
        }
        return new File(resourceUrl.toURI());
    }
}