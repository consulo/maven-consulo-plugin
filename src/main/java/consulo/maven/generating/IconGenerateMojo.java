package consulo.maven.generating;

import ar.com.hjg.pngj.PngReader;
import com.kitfox.svg.SVGDiagram;
import com.kitfox.svg.SVGRoot;
import com.kitfox.svg.SVGUniverse;
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

import javax.lang.model.element.Modifier;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * @author VISTALL
 * @since 2020-09-26
 */
@Mojo(name = "generate-icon", threadSafe = true, requiresDependencyResolution = ResolutionScope.NONE)
public class IconGenerateMojo extends GenerateMojo
{
	private static class GenerateInfo
	{
		public List<File> files;

		public File baseDir;

		public String id;
	}

	private static class IconInfo
	{
		public String id;

		public String fieldName;

		public int width;

		public int height;
	}

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
			List<GenerateInfo> toGenerateFiles = new ArrayList<>();

			if(log.isDebugEnabled())
			{
				log.debug("Analyzing: " + mavenProject.getCompileSourceRoots());
			}

			for(Resource resource : mavenProject.getResources())
			{
				File srcDirectory = new File(resource.getDirectory());

				File iconDir = new File(srcDirectory, "icon");

				if(!iconDir.exists())
				{
					continue;
				}

				File idFile = new File(iconDir, "id.txt");
				if(!idFile.exists())
				{
					continue;
				}

				String iconLibraryAndId = FileUtils.fileRead(idFile, "UTF-8");

				String[] split = iconLibraryAndId.split(":");

				String iconLibrary = split[0];
				if(!"Default".equals(iconLibrary))
				{
					continue;
				}

				String path = split[1].replace(".", "/");

				File directory = new File(iconDir, path);

				List<File> files = FileUtils.getFiles(directory, "**/*.svg,**/*.png", null);

				GenerateInfo g = new GenerateInfo();
				g.baseDir = directory;
				g.id = split[1];
				g.files = files;

				toGenerateFiles.add(g);
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
			File outputDirectoryFile = new File(outputDirectory, "generated-sources/icon");

			outputDirectoryFile.mkdirs();

			CacheIO logic = new CacheIO(mavenProject, "icon.cache");
			if(TEST_GENERATE)
			{
				logic.delete();
			}
			logic.read();

			mavenProject.addCompileSourceRoot(outputDirectoryFile.getPath());

			SVGUniverse svgUniverse = new SVGUniverse();

			for(GenerateInfo info : toGenerateFiles)
			{
				String id = info.id;
				List<File> files = info.files;
				File sourceDirectory = info.baseDir;

				boolean isAllUp = true;

				for(File file : files)
				{
					if(!logic.isUpToDate(file))
					{
						isAllUp = false;
						break;
					}
				}

				if(isAllUp)
				{
					log.info("IconLibrary: " + id + " is up to date");
					continue;
				}

				Map<String, IconInfo> icons = new TreeMap<>();
				Path sourcePath = sourceDirectory.toPath();

				for(File file : files)
				{
					String name = file.getName();
					if(name.endsWith("@2x.svg") || name.endsWith("@2x.png"))
					{
						// ignore bigger icons
						continue;
					}

					if(name.endsWith("_dark.svg") || name.endsWith("_dark.png"))
					{
						log.info("IconLibrary: " + file.getPath() + " unused dark icon");
						continue;
					}

					Path parentPath = file.toPath();

					String relativePath = sourcePath.relativize(parentPath).toString();
					if(relativePath == null)
					{
						log.info("IconLibrary: " + file.getPath() + " can't calculate relative path to " + sourceDirectory);
						continue;
					}

					int width, height;
					if(relativePath.endsWith(".svg"))
					{
						relativePath = relativePath.replace(".svg", "");

						SVGDiagram diagram = svgUniverse.getDiagram(file.toURI());

						SVGRoot root = diagram.getRoot();

						height = (int) root.getDeviceHeight();
						width = (int) root.getDeviceWidth();
					}
					else if(relativePath.endsWith(".png"))
					{
						relativePath = relativePath.replace(".png", "");

						PngReader reader = null;
						try (InputStream stream = new FileInputStream(file))
						{
							reader = new PngReader(stream);
							width = reader.imgInfo.cols;
							height = reader.imgInfo.rows;
						}
						finally
						{
							if(reader != null)
							{
								reader.close();
							}
						}
					}
					else
					{
						throw new UnsupportedOperationException(relativePath);
					}

					String fieldName = relativePath.replace("\\", "/").replace("/", "_");
					String iconId = relativePath.replace("\\", "/").replace("/", ".");
					IconInfo iconInfo = new IconInfo();
					iconInfo.fieldName = fieldName.replace("-", "_");
					iconInfo.id = iconId.replace("-", "_");


					iconInfo.width = width;
					iconInfo.height = height;
					icons.put(fieldName, iconInfo);
				}

				String parentPackage = id.substring(0, id.lastIndexOf("."));
				String name = id.substring(id.lastIndexOf(".") + 1, id.length());

				log.info("IconLibrary: Generated file: " + id + " to " + outputDirectoryFile.getPath());

				for(File file : files)
				{
					logic.putCacheEntry(file);
				}

				//ClassName imageClazz = ClassName.get("consulo.ui.image", "Image");
				ClassName imageKeyClass = ClassName.get("consulo.ui.image", "ImageKey");

				List<FieldSpec> fieldSpecs = new ArrayList<>();
				List<MethodSpec> methodSpecs = new ArrayList<>();

				for(IconInfo iconInfo : icons.values())
				{
					FieldSpec.Builder fieldSpec = FieldSpec.builder(imageKeyClass, iconInfo.fieldName, Modifier.PRIVATE, Modifier.STATIC, Modifier.FINAL);
					fieldSpec.initializer(CodeBlock.builder().add("$T.of($S, $S, $L, $L)", imageKeyClass, id, iconInfo.id, iconInfo.width, iconInfo.height).build());
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

		File projectDir = new File("W:\\_github.com\\consulo\\consulo\\modules\\base\\icon\\base-icon-library");
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
