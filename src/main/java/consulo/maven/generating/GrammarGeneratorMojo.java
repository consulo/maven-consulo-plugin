package consulo.maven.generating;

import maven.bnf.consulo.devkit.grammarKit.generator.GenerateTarget;
import org.apache.maven.model.Build;
import org.apache.maven.model.Resource;
import org.apache.maven.plugin.logging.SystemStreamLog;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;

import java.io.File;
import java.util.EnumSet;
import java.util.Set;

/**
 * @author VISTALL
 * @since 2018-06-18
 */
@Mojo(name = "generate-parsers", threadSafe = true, requiresDependencyResolution = ResolutionScope.NONE)
@Deprecated
public class GrammarGeneratorMojo extends AbstractGrammarGeneratorMojo {

    public static void main(String[] args) throws Exception {
        MavenProject mavenProject = new MavenProject();

        File projectDir = new File("W:\\ConsulorRepos\\consulo-devkit\\grammar-kit-core");
        Resource resource = new Resource();
        resource.setDirectory(new File(projectDir, "src\\main\\resources").getPath());
        Build build = new Build();
        build.addResource(resource);
        build.setSourceDirectory(new File(projectDir, "src\\main\\java").getPath());
        build.setOutputDirectory(new File(projectDir, "target").getAbsolutePath());
        build.setDirectory(new File(projectDir, "target").getAbsolutePath());
        mavenProject.setBuild(build);

        GrammarGeneratorMojo grammarGeneratorMojo = new GrammarGeneratorMojo();
        grammarGeneratorMojo.myMavenProject = mavenProject;
        grammarGeneratorMojo.setLog(new SystemStreamLog());

        grammarGeneratorMojo.execute();
    }

    @Override
    public String getOutputDirName() {
        return "parsers";
    }

    @Override
    public Set<GenerateTarget> getGenerateTargets() {
        return EnumSet.allOf(GenerateTarget.class);
    }
}
