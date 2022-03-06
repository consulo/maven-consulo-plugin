package consulo.maven.generating;

import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.util.text.StringUtil;
import com.squareup.javapoet.*;
import consulo.maven.base.util.cache.CacheIO;
import org.apache.maven.model.Build;
import org.apache.maven.model.Resource;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugin.logging.SystemStreamLog;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.FileUtils;
import org.yaml.snakeyaml.Yaml;

import javax.lang.model.element.Modifier;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.text.Format;
import java.text.MessageFormat;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * @author VISTALL
 * @since 2020-05-21
 */
@Mojo(name = "generate-localize", threadSafe = true, requiresDependencyResolution = ResolutionScope.NONE)
public class LocalizeGeneratorMojo extends GenerateMojo
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
			List<Map.Entry<File, File>> toGenerateFiles = new ArrayList<>();

			if(log.isDebugEnabled())
			{
				log.debug("Analyzing: " + mavenProject.getCompileSourceRoots());
			}

			for(Resource resource : mavenProject.getResources())
			{
				File srcDirectory = new File(resource.getDirectory());

				File localizeDir = new File(srcDirectory, "LOCALIZE-LIB");

				File enUSDir = new File(localizeDir, "en_US");
				if(!enUSDir.exists())
				{
					// skip not en_US localize
					continue;
				}

				List<File> files = FileUtils.getFiles(enUSDir, "*.yaml", null);
				for(File file : files)
				{
					toGenerateFiles.add(new AbstractMap.SimpleImmutableEntry<>(file, srcDirectory));
				}

				if(files.isEmpty())
				{
					throw new MojoFailureException("LocalizeLibrary: " + enUSDir + " exists, but no files.");
				}
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
			if(TEST_GENERATE)
			{
				logic.delete();
			}
			logic.read();

			mavenProject.addCompileSourceRoot(outputDirectoryFile.getPath());

			for(Map.Entry<File, File> info : toGenerateFiles)
			{
				File file = info.getKey();
				File sourceDirectory = info.getValue();

				if(logic.isUpToDate(file))
				{
					log.info("Localize: " + file.getPath() + " is up to date");
					continue;
				}

				// consulo.platform.base.ApplicationLocalize
				String localizeFullPath = FileUtilRt.getNameWithoutExtension(file.getName());

				String pluginId = StringUtil.getPackageName(localizeFullPath);
				String localizeId = StringUtil.getShortName(localizeFullPath);

				String packageName = pluginId + ".localize";

				log.info("Localize: Generated file: " + file.getPath() + " to " + outputDirectoryFile.getPath());

				logic.putCacheEntry(file);

				ClassName localizeKey = ClassName.get("consulo.localize", "LocalizeKey");
				ClassName localizeValue = ClassName.get("consulo.localize", "LocalizeValue");

				List<FieldSpec> fieldSpecs = new ArrayList<>();
				List<MethodSpec> methodSpecs = new ArrayList<>();

				FieldSpec.Builder idField = FieldSpec.builder(String.class, "ID", Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL);
				idField.initializer(CodeBlock.of("$S", localizeFullPath));
				fieldSpecs.add(idField.build());

				Yaml yaml = new Yaml();
				try (InputStream stream = new FileInputStream(file))
				{
					Map<String, Map<String, String>> o = yaml.load(stream);

					for(Map.Entry<String, Map<String, String>> entry : o.entrySet())
					{
						String key = entry.getKey();

						Map<String, String> value = entry.getValue();

						String t = value.get("text");
						String text = t == null ? "" : t;

						String fieldName = normalizeName(key.replace(".", "_").replace(" ", "_"));

						FieldSpec.Builder fieldSpec = FieldSpec.builder(localizeKey, fieldName, Modifier.PRIVATE, Modifier.STATIC, Modifier.FINAL);
						fieldSpec.initializer(CodeBlock.builder().add("$T.of($L, $S)", localizeKey, "ID", key).build());
						fieldSpecs.add(fieldSpec.build());

						String methodName = normalizeName(captilizeByDot(key));

						MessageFormat format = null;
						try
						{
							format = new MessageFormat(text);
						}
						catch(Exception e)
						{
							throw new MojoFailureException("Failed to parse text: " + text, e);
						}

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
							methodSpec.addStatement("$L", argCall.toString());
						}
						else
						{
							methodSpec.addStatement("$L", "return " + fieldName + ".getValue()");
						}
						methodSpecs.add(methodSpec.build());
					}
				}
				catch(Exception e)
				{
					throw new MojoFailureException(e.getMessage(), e);
				}

				TypeSpec typeSpec = TypeSpec.classBuilder(localizeId)
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

	public static void main(String[] args)
	{
		TEST_GENERATE = true;

		MavenProject mavenProject = new MavenProject();

		File projectDir = new File("W:\\_github.com\\consulo\\consulo\\modules\\base\\base-localize-library");
		Resource resource = new Resource();
		resource.setDirectory(new File(projectDir, "src\\main\\resources").getPath());
		Build build = new Build();
		build.addResource(resource);
		build.setOutputDirectory(new File(projectDir, "target").getAbsolutePath());
		build.setDirectory(new File(projectDir, "target").getAbsolutePath());
		mavenProject.setBuild(build);

		generate(new SystemStreamLog(), mavenProject);
	}
}
