package consulo.maven.generating;

import consulo.internal.org.objectweb.asm.*;
import org.apache.maven.model.Build;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * @author VISTALL
 * @since 17-Jun-22
 */
@Mojo(name = "patch-bind-module-info", defaultPhase = LifecyclePhase.PROCESS_CLASSES, requiresOnline = false, requiresDependencyResolution = ResolutionScope.COMPILE, threadSafe = true)
public class PatchBindModuleInfoMojo extends AbstractMojo
{
	private static final String INJECTING_BINDING = "consulo/component/bind/InjectingBinding";

	@Parameter(property = "project", defaultValue = "${project}")
	private MavenProject myMavenProject;

	@Override
	public void execute() throws MojoExecutionException, MojoFailureException
	{
		String outputDirectory = myMavenProject.getBuild().getOutputDirectory();

		File moduleInfoClass = new File(outputDirectory, "classes/module-info.class");
		if(!moduleInfoClass.exists())
		{
			getLog().info(moduleInfoClass + " not exists");
			return;
		}

		File servicesFile = new File(outputDirectory, "classes/META-INF/services/" + INJECTING_BINDING.replace("/", "."));
		if(!servicesFile.exists())
		{
			getLog().info(servicesFile + " not exists");
			return;
		}

		List<String> lines;
		try
		{
			lines = Files.readAllLines(servicesFile.toPath(), StandardCharsets.UTF_8);
		}
		catch(IOException e)
		{
			throw new MojoFailureException(e.getMessage(), e);
		}

		byte[] moduleInfoBytes;
		try
		{
			moduleInfoBytes = Files.readAllBytes(moduleInfoClass.toPath());
		}
		catch(IOException e)
		{
			throw new MojoFailureException(e.getMessage(), e);
		}

		ClassReader reader = new ClassReader(moduleInfoBytes);

		ClassWriter writer = new ClassWriter(reader, ClassWriter.COMPUTE_MAXS);

		ClassVisitor classVisitor = new ClassVisitor(Opcodes.API_VERSION, writer)
		{
			@Override
			public ModuleVisitor visitModule(String name, int access, String version)
			{
				ModuleVisitor superVisitor = super.visitModule(name, access, version);
				return new ModuleVisitor(Opcodes.API_VERSION, superVisitor)
				{
					private boolean myFoundBindings = false;

					@Override
					public void visitProvide(String service, String... providers)
					{
						if(service.equals(INJECTING_BINDING))
						{
							myFoundBindings = true;

							List<String> classes = lines.stream().filter(s -> !s.startsWith("#") && !s.trim().isEmpty()).map(s -> s.replace(".", "/")).collect(Collectors.toList());

							Set<String> all = new TreeSet<>(classes);
							all.addAll(List.of(providers));

							providers = all.toArray(new String[all.size()]);
						}

						super.visitProvide(service, providers);
					}

					@Override
					public void visitEnd()
					{
						if(!myFoundBindings)
						{
							String[] classes = lines.stream().filter(s -> !s.startsWith("#") && !s.trim().isEmpty()).map(s -> s.replace(".", "/")).toArray(String[]::new);
							mv.visitProvide(INJECTING_BINDING, classes);
						}
						super.visitEnd();
					}
				};
			}

			@Override
			public void visitEnd()
			{
				super.visitEnd();
			}
		};

		reader.accept(classVisitor, ClassReader.EXPAND_FRAMES);

		byte[] bytes = writer.toByteArray();

		File moduleInfoClass2 = moduleInfoClass;

		try
		{
			getLog().info("patched module-info.class " + moduleInfoClass2);
			Files.write(moduleInfoClass2.toPath(), bytes, StandardOpenOption.CREATE);
		}
		catch(IOException e)
		{
			throw new MojoFailureException(e.getMessage(), e);
		}
	}

	public static void main(String[] args) throws Exception
	{
		MavenProject mavenProject = new MavenProject();

		File projectDir = new File("W:\\_github.com\\consulo\\consulo\\modules\\base\\ide-impl");

		Build build = new Build();
		build.setOutputDirectory(new File(projectDir, "target").getAbsolutePath());
		build.setDirectory(new File(projectDir, "target").getAbsolutePath());
		mavenProject.setBuild(build);

		PatchBindModuleInfoMojo mojo = new PatchBindModuleInfoMojo();
		mojo.myMavenProject = mavenProject;

		mojo.execute();
	}
}
