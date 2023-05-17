package consulo.maven.packaging;

import consulo.maven.base.util.ExtractUtil;
import consulo.maven.base.util.HubApiUtil;
import consulo.maven.base.util.RepositoryNode;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;
import org.apache.commons.compress.utils.IOUtils;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.FileUtils;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Objects;
import java.util.Set;

/**
 * @author VISTALL
 * @since 24-Nov-17
 */
@Mojo(name = "workspace", threadSafe = true, requiresDependencyResolution = ResolutionScope.COMPILE, defaultPhase = LifecyclePhase.PACKAGE)
public class WorkspaceMojo extends AbstractPackagingMojo
{
	public static File getDependenciesDirectory(MavenProject mavenProject)
	{
		String directory = mavenProject.getBuild().getDirectory();

		return new File(directory, "consulo-plugin-dependencies");
	}

	public static File getExtractedDirectory(MavenProject mavenProject)
	{
		String directory = mavenProject.getBuild().getDirectory();

		return new File(directory, "consulo-plugin-extracted");
	}

	@Override
	public void execute() throws MojoExecutionException, MojoFailureException
	{
		if(getLog().isDebugEnabled())
		{
			getLog().info("Dependencies=" + myDependencies + ", skip=" + packaging.skip);
		}

		if(!myDependencies.isEmpty() && packaging.skip)
		{
			createDependenciesWorkspace();
		}

		File targetDirectory = getExtractedDirectory(myProject);

		FileUtils.mkdir(targetDirectory.getPath());

		try
		{
			FileUtils.deleteDirectory(targetDirectory);

			FileUtils.mkdir(targetDirectory.getPath());

			Artifact artifact = myProject.getArtifact();
			if(artifact == null)
			{
				throw new MojoFailureException("No project artifact");
			}

			File file = artifact.getFile();
			if(file == null || !file.exists())
			{
				throw new MojoFailureException("Project artifact is not build");
			}

			File pluginDirectory = new File(targetDirectory, myId);

			File libDirectory = new File(pluginDirectory, "lib");

			FileUtils.mkdir(libDirectory.getPath());

			FileUtils.copyFile(file, new File(libDirectory, file.getName()));

			MetaFiles metaFiles = new MetaFiles();
			metaFiles.readFromJar(file);

			Set<Artifact> dependencyArtifacts = myProject.getDependencyArtifacts();
			for(Artifact dependencyArtifact : dependencyArtifacts)
			{
				if(isValidArtifactForPackaging(dependencyArtifact))
				{
					getLog().debug("Dependency artifact: " + dependencyArtifact.getFile());

					File artifactFile = getAndCheckArtifactFile(dependencyArtifact);

					if(artifactFile.isDirectory())
					{
						// classes directory we need pack it to jar
						String artifactName = dependencyArtifact.getArtifactId() + "-" + dependencyArtifact.getBaseVersion() + ".jar";
						File jarArchiveFile = new File(libDirectory, artifactName);

						try (ZipArchiveOutputStream zipStream = new ZipArchiveOutputStream(jarArchiveFile))
						{
							Path classesDir = artifactFile.toPath();
							Files.walkFileTree(classesDir, new SimpleFileVisitor<Path>()
							{
								@Override
								public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException
								{
									String relativePath = classesDir.relativize(file).toString().replace("\\", "/");
									ZipArchiveEntry entry = new ZipArchiveEntry(relativePath);
									zipStream.putArchiveEntry(entry);

									try (InputStream stream = Files.newInputStream(file))
									{
										IOUtils.copy(stream, zipStream);
										zipStream.closeArchiveEntry();
									}

									return super.visitFile(file, attrs);
								}
							});
						}

						artifactFile = jarArchiveFile;
					}
					else
					{
						FileUtils.copyFile(artifactFile, new File(libDirectory, artifactFile.getName()));
					}

					metaFiles.readFromJar(artifactFile);

					String pluginRequiresXml = readPluginRequires(artifactFile);
					if(pluginRequiresXml != null)
					{
						File requiresXmlFile = new File(libDirectory, artifactFile.getName() + REQUIRES_EXTENSION);
						Files.writeString(requiresXmlFile.toPath(), pluginRequiresXml, StandardCharsets.UTF_8);
					}
				}
			}

			metaFiles.forEachData((filePath, data) ->
			{
				try
				{
					File outFile = new File(pluginDirectory, filePath);
					outFile.getParentFile().mkdirs();
					Files.writeString(outFile.toPath(), data, StandardCharsets.UTF_8);
				}
				catch(IOException e)
				{
					throw new IllegalArgumentException(e);
				}
			});

			File distDirectory = new File(myProject.getBasedir(), "src/main/dist");
			if(distDirectory.exists())
			{
				FileUtils.copyDirectoryStructure(distDirectory, new File(targetDirectory, myId));
			}

			for(Copy copy : packaging.copies)
			{
				Artifact copyArtifact = resolveArtifact(copy.artifact);

				File artifactFile = getAndCheckArtifactFile(copyArtifact);

				FileUtils.copyFile(artifactFile, new File(pluginDirectory, getRelativePathForCopy(copy, artifactFile)));
			}
		}
		catch(IOException e)
		{
			throw new MojoFailureException(e.getMessage(), e);
		}
	}

	private void createDependenciesWorkspace() throws MojoFailureException
	{
		File targetDirectory = getDependenciesDirectory(myProject);

		FileUtils.mkdir(targetDirectory.getPath());

		String consuloVersion = PatchPluginXmlMojo.findConsuloVersion(myProject);

		for(String dependencyId : myDependencies)
		{
			logPluginStep(dependencyId, "checking...");

			if(mySession.isOffline())
			{
				File versionCheckFile = new File(targetDirectory, dependencyId + ".version");

				if(!versionCheckFile.exists())
				{
					logPluginStep(dependencyId, "not found");
					throw new MojoFailureException(dependencyId + " not found");
				}
			}
			else
			{
				RepositoryNode repositoryNode = HubApiUtil.requestRepositoryNodeInfo(myRepositoryChannel, myApiUrl, dependencyId, consuloVersion, null);
				if(repositoryNode == null)
				{
					throw new MojoFailureException("Dependency is not found. Id: " + dependencyId + ", consuloVersion: " + consuloVersion + ", channel: " + myRepositoryChannel);
				}

				File versionCheckFile = new File(targetDirectory, dependencyId + ".version");
				if(versionCheckFile.exists())
				{
					try
					{
						String versionFromFile = FileUtils.fileRead(versionCheckFile);

						if(Objects.equals(versionFromFile, repositoryNode.version))
						{
							logPluginStep(dependencyId, "version not changed");
							continue;
						}
					}
					catch(IOException e)
					{
						versionCheckFile.delete();

						getLog().warn(e);
					}
				}

				try
				{
					File tempFile = File.createTempFile("consulo-plugin", ".zip");
					tempFile.deleteOnExit();

					logPluginStep(dependencyId, "downloading...");

					HubApiUtil.downloadRepositoryNode(myRepositoryChannel, myApiUrl, dependencyId, consuloVersion, null, tempFile);

					File dependencyDirectory = new File(targetDirectory, dependencyId);

					dependencyDirectory.delete();

					logPluginStep(dependencyId, "extracting...");

					ExtractUtil.extractZip(tempFile, targetDirectory);

					FileUtils.fileWrite(versionCheckFile.getPath(), repositoryNode.version);

					logPluginStep(dependencyId, "extracted");
				}
				catch(Exception e)
				{
					throw new MojoFailureException(e.getMessage(), e);
				}
			}
		}
	}

	private void logPluginStep(String pluginId, String info)
	{
		getLog().info(String.format("[%s] %s", pluginId, info));
	}
}
