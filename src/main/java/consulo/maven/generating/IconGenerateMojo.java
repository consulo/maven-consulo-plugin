package consulo.maven.generating;

import com.squareup.javapoet.*;
import org.apache.maven.model.Build;
import org.apache.maven.model.Resource;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugin.logging.SystemStreamLog;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;

import javax.lang.model.element.Modifier;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * @author VISTALL
 * @since 2020-09-26
 */
@Mojo(name = "generate-icon", threadSafe = true, requiresDependencyResolution = ResolutionScope.NONE)
public class IconGenerateMojo extends AbstractIconGeneratorMojo
{
	@Override
	public void execute() throws MojoExecutionException, MojoFailureException
	{
		execute(getLog(), myMavenProject);
	}

	@Override
	protected void validateGeneration(Map<String, List<GenerateInfo>> toGenerateFiles) throws MojoFailureException
	{
		if(!toGenerateFiles.containsKey("light"))
		{
			throw new MojoFailureException("IconLibrary: no 'light' theme icons");
		}
	}

	@Override
	protected void generate(String themeId, String parentPackage, String name, String id, Log log, Map<String, IconInfo> icons, File outputDirectoryFile) throws IOException
	{
		if(!themeId.equals("light"))
		{
			return;
		}

		ClassName imageKeyClass = ClassName.get("consulo.ui.image", "ImageKey");

		List<FieldSpec> fieldSpecs = new ArrayList<>();
		List<MethodSpec> methodSpecs = new ArrayList<>();

		FieldSpec.Builder idField = FieldSpec.builder(String.class, "ID", Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL);
		idField.initializer(CodeBlock.of("$S", id));
		fieldSpecs.add(idField.build());

		for(IconInfo iconInfo : icons.values())
		{
			FieldSpec.Builder fieldSpec = FieldSpec.builder(imageKeyClass, iconInfo.fieldName, Modifier.PRIVATE, Modifier.STATIC, Modifier.FINAL);
			fieldSpec.initializer(CodeBlock.builder().add("$T.of($L, $S, $L, $L)", imageKeyClass, "ID", iconInfo.id.toLowerCase(Locale.ROOT), iconInfo.width, iconInfo.height).build());
			fieldSpecs.add(fieldSpec.build());
		}

		for(IconInfo iconInfo : icons.values())
		{
			MethodSpec.Builder methodSpec = MethodSpec.methodBuilder(captilizeByDot(iconInfo.id));
			methodSpec.addModifiers(Modifier.PUBLIC, Modifier.STATIC);
			methodSpec.returns(imageKeyClass);
			methodSpec.addStatement("$L", "return " + iconInfo.fieldName);

			methodSpecs.add(methodSpec.build());
		}

		TypeSpec typeSpec = TypeSpec.classBuilder(name)
				.addAnnotation(AnnotationSpec.builder(SuppressWarnings.class).addMember("value", "$S", "ALL").build())
				.addModifiers(Modifier.PUBLIC, Modifier.FINAL)
				.addFields(fieldSpecs)
				.addMethods(methodSpecs)
				.addJavadoc("Generated code. Don't edit this class")
				.build();

		JavaFile javaFile = JavaFile.builder(parentPackage + ".icon", typeSpec)
				.build();

		javaFile.writeTo(outputDirectoryFile);
	}

	public static void main(String[] args) throws Exception
	{
		TEST_GENERATE = true;

		MavenProject mavenProject = new MavenProject();

		File projectDir = new File("W:\\_github.com\\consulo\\consulo\\modules\\base\\base-icon-library");
		Resource resource = new Resource();
		resource.setDirectory(new File(projectDir, "src\\main\\resources").getPath());
		Build build = new Build();
		build.addResource(resource);
		build.setOutputDirectory(new File(projectDir, "target").getAbsolutePath());
		build.setDirectory(new File(projectDir, "target").getAbsolutePath());
		mavenProject.setBuild(build);

		new IconGenerateMojo().execute(new SystemStreamLog(), mavenProject);
	}
}
