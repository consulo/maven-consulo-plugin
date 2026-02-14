package consulo.maven.generating;

import maven.bnf.consulo.devkit.grammarKit.generator.GenerateTarget;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.ResolutionScope;

import java.util.Set;

/**
 * @author VISTALL
 * @since 2026-02-14
 */
@Mojo(name = "generate-parser-impl", threadSafe = true, requiresDependencyResolution = ResolutionScope.NONE)
public class GenerateParserImplMojo extends AbstractGrammarGeneratorMojo {
    @Override
    public String getOutputDirName() {
        return "parser-impl";
    }

    @Override
    public Set<GenerateTarget> getGenerateTargets() {
        return Set.of(GenerateTarget.Impl);
    }
}
