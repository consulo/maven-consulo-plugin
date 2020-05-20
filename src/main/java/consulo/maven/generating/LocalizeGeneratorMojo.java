package consulo.maven.generating;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.text.Format;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.lang.model.element.Modifier;

import org.apache.maven.model.Build;
import org.apache.maven.model.Resource;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugin.logging.SystemStreamLog;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;
import org.yaml.snakeyaml.Yaml;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.squareup.javapoet.AnnotationSpec;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.TypeSpec;
import consulo.maven.base.util.cache.CacheIO;

/**
 * @author VISTALL
 * @since 2020-05-21
 */
@Mojo(name = "generate-localize", threadSafe = true, requiresDependencyResolution = ResolutionScope.NONE)
public class LocalizeGeneratorMojo extends AbstractMojo
{
	@Parameter(property = "project", defaultValue = "${project}")
	private MavenProject myMavenProject;

	@Override
	public void execute() throws MojoExecutionException, MojoFailureException
	{
		generate(getLog(), myMavenProject);
	}

	private static void generate(Log log, MavenProject mavenProject)
	{
		try
		{
			List<Pair<File, File>> toGenerateFiles = new ArrayList<>();

			if(log.isDebugEnabled())
			{
				log.debug("Analyzing: " + mavenProject.getCompileSourceRoots());
			}

			for(Resource resource : mavenProject.getResources())
			{
				File srcDirectory = new File(resource.getDirectory());

				File localizeDir = new File(srcDirectory, "localize");

				if(!localizeDir.exists())
				{
					continue;
				}

				File idFile = new File(localizeDir, "id.txt");
				if(!idFile.exists())
				{
					continue;
				}

				String txt = FileUtil.loadFile(idFile, StandardCharsets.UTF_8);

				// ignore not en localize
				if(!"en".equals(txt))
				{
					continue;
				}

				FileUtil.visitFiles(srcDirectory, file ->
				{
					if("yaml".equals(FileUtil.getExtension((CharSequence) file.getName())))
					{
						toGenerateFiles.add(Pair.create(file, srcDirectory));
					}

					return true;
				});
			}

			if(log.isDebugEnabled())
			{
				log.debug("Files for generate: " + toGenerateFiles);
			}

			if(toGenerateFiles.isEmpty())
			{
				return;
			}

			String outputDirectory = mavenProject.getBuild().getDirectory();
			File outputDirectoryFile = new File(outputDirectory, "generated-sources/localize");

			outputDirectoryFile.mkdirs();

			CacheIO logic = new CacheIO(mavenProject, "localize.cache");

			logic.read();

			mavenProject.addCompileSourceRoot(outputDirectoryFile.getPath());

			for(Pair<File, File> info : toGenerateFiles)
			{
				File file = info.getFirst();
				File sourceDirectory = info.getSecond();

				if(logic.isUpToDate(file))
				{
					log.info("Localize: " + file.getPath() + " is up to date");
					continue;
				}

				String relativePath = FileUtil.getRelativePath(sourceDirectory, file.getParentFile());
				if(relativePath == null)
				{
					log.info("Localize: " + file.getPath() + " can't calculate relative path to " + sourceDirectory);
					continue;
				}

				String pluginId = relativePath.replace("\\", "/").replace("localize/", "").replace("/", ".");
				String packageName = pluginId + ".localize";

				log.info("Localize: Generated file: " + file.getPath() + " to " + outputDirectoryFile.getPath());

				logic.putCacheEntry(file);

				ClassName localizeKey = ClassName.get("consulo.localize", "LocalizeKey");
				ClassName localizeValue = ClassName.get("consulo.localize", "LocalizeValue");

				String localizeName = FileUtil.getNameWithoutExtension(file);

				List<FieldSpec> fieldSpecs = new ArrayList<>();
				List<MethodSpec> methodSpecs = new ArrayList<>();

				Yaml yaml = new Yaml();
				try (InputStream stream = new FileInputStream(file))
				{
					Map<String, Map<String, String>> o = yaml.load(stream);

					for(Map.Entry<String, Map<String, String>> entry : o.entrySet())
					{
						String key = entry.getKey();

						Map<String, String> value = entry.getValue();

						String text = StringUtil.notNullize(value.get("text"));

						String fieldName = key.replace(".", "_");

						FieldSpec.Builder fieldSpec = FieldSpec.builder(localizeKey, fieldName, Modifier.PRIVATE, Modifier.STATIC, Modifier.FINAL);
						fieldSpec.initializer(CodeBlock.builder().add("$T.of($S, $S)", localizeKey, pluginId + "." + localizeName, key).build());
						fieldSpecs.add(fieldSpec.build());

						String methodName = captilizeByDot(key);

						MessageFormat format = new MessageFormat(text);

						Format[] formatsByArgumentIndex = format.getFormatsByArgumentIndex();
						
						MethodSpec.Builder methodSpec = MethodSpec.methodBuilder(methodName);
						methodSpec.addModifiers(Modifier.PUBLIC, Modifier.STATIC);
						methodSpec.returns(localizeValue);
						if(formatsByArgumentIndex.length > 0)
						{
							StringBuilder argCall = new StringBuilder("return " + fieldName + ".getValue(");

							for(int i = 0; i < formatsByArgumentIndex.length; i++)
							{
								String parameterName = "arg" + i;

								methodSpec.addParameter(Object.class, parameterName);

								if(i != 0)
								{
									argCall.append(", ");
								}
								
								argCall.append(parameterName);
							}

							argCall.append(")");
							methodSpec.addStatement(argCall.toString());
						}
						else
						{
							methodSpec.addStatement("return " + fieldName + ".getValue()");
						}
						methodSpecs.add(methodSpec.build());
					}
				}
				catch(Exception e)
				{
					log.error(e);
				}

				TypeSpec typeSpec = TypeSpec.classBuilder(localizeName)
						.addAnnotation(AnnotationSpec.builder(SuppressWarnings.class).addMember("value", "$S", "ALL").build())
						.addModifiers(Modifier.PUBLIC, Modifier.FINAL)
						.addFields(fieldSpecs)
						.addMethods(methodSpecs)
						.addJavadoc("Generated code. Don't edit this class")
						.build();

				JavaFile javaFile = JavaFile.builder(packageName, typeSpec)
						.build();

				javaFile.writeTo(outputDirectoryFile);
			}

			logic.write();
		}
		catch(Exception e)
		{
			log.error(e);
		}
	}

	private static String captilizeByDot(String id)
	{
		String[] split = id.split("\\.");

		StringBuilder builder = new StringBuilder();
		for(int i = 0; i < split.length; i++)
		{
			if(i != 0)
			{
				builder.append(StringUtil.capitalize(split[i]));
			}
			else
			{
				builder.append(split[i]);
			}
		}

		return builder.toString();
	}

	public static void main(String[] args)
	{
		MavenProject mavenProject = new MavenProject();

		File projectDir = new File("W:\\_github.com\\consulo\\consulo\\modules\\base\\base-localize-library");
		mavenProject.addCompileSourceRoot(new File(projectDir, "src\\main\\resources").getPath());
		Build build = new Build();
		build.setOutputDirectory(new File(projectDir, "target").getAbsolutePath());
		build.setDirectory(new File(projectDir, "target").getAbsolutePath());
		mavenProject.setBuild(build);

		generate(new SystemStreamLog(), mavenProject);
	}
}
