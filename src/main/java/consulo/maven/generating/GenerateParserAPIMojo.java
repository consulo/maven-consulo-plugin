package consulo.maven.generating;

import maven.bnf.consulo.devkit.grammarKit.generator.GenerateTarget;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.ResolutionScope;

import java.util.Set;

/**
 * @author VISTALL
 * @since 2026-02-14
 */
@Mojo(name = "generate-parser-api", threadSafe = true, requiresDependencyResolution = ResolutionScope.NONE)
public class GenerateParserAPIMojo extends AbstractGrammarGeneratorMojo {
    @Override
    public String getOutputDirName() {
        return "parser-api";
    }

    @Override
    public Set<GenerateTarget> getGenerateTargets() {
        return Set.of(GenerateTarget.API);
    }
}
