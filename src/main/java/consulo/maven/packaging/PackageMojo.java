package consulo.maven.packaging;

import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;
import org.apache.commons.compress.utils.IOUtils;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.codehaus.plexus.util.FileUtils;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.function.Function;

/**
 * @author VISTALL
 * @since 24-Nov-17
 */
@Mojo(name = "package", threadSafe = true, requiresDependencyResolution = ResolutionScope.COMPILE, defaultPhase = LifecyclePhase.PACKAGE)
public class PackageMojo extends AbstractPackagingMojo
{
	@Override
	public void execute() throws MojoExecutionException, MojoFailureException
	{
		patchPluginXml();

		String directory = myProject.getBuild().getDirectory();

		File targetFile = new File(directory, myId + ".consulo-plugin");

		FileUtils.mkdir(directory);

		try (ZipArchiveOutputStream zipStream = new ZipArchiveOutputStream(targetFile))
		{
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

			writeRuntimeFile(zipStream, file);

			Set<Artifact> dependencyArtifacts = myProject.getDependencyArtifacts();
			for(Artifact dependencyArtifact : dependencyArtifacts)
			{
				if(isValidArtifactForPackaging(dependencyArtifact))
				{
					File artifactFile = getAndCheckArtifactFile(dependencyArtifact);

					writeRuntimeFile(zipStream, artifactFile);

					String requiresXml = getPluginRequiresXml(artifactFile);
					if(requiresXml != null)
					{
						writeText(zipStream, artifactFile.getName() + REQUIRES_EXTENSION, requiresXml);
					}
				}
			}

			File distDirectory = new File(myProject.getBasedir(), "src/main/dist");
			if(distDirectory.exists())
			{
				archiveFileOrDirectory(zipStream, distDirectory, child ->
				{
					String path = distDirectory.getPath();
					String childPath = child.getPath();
					// + 1 - eat path separator
					return childPath.substring(path.length() + 1, childPath.length());
				});
			}

			for(Copy copy : packaging.copies)
			{
				Artifact copyArtifact = resolveArtifact(copy.artifact);

				File artifactFile = getAndCheckArtifactFile(copyArtifact);

				archiveFileOrDirectory(zipStream, artifactFile, it -> getRelativePathForCopy(copy, artifactFile));
			}
		}
		catch(IOException e)
		{
			throw new MojoFailureException(e.getMessage(), e);
		}
	}

	private void archiveFileOrDirectory(ZipArchiveOutputStream zipStream, File file, Function<File, String> relativePathFunc) throws IOException
	{
		if(file.isFile())
		{
			ArchiveEntry entry = zipStream.createArchiveEntry(file, myId + "/" + relativePathFunc.apply(file));

			zipStream.putArchiveEntry(entry);

			try (FileInputStream fileInputStream = new FileInputStream(file))
			{
				IOUtils.copy(fileInputStream, zipStream);
			}

			zipStream.closeArchiveEntry();
		}
		else if(file.isDirectory())
		{
			File[] files = file.listFiles();
			assert files != null;
			for(File child : files)
			{
				archiveFileOrDirectory(zipStream, child, relativePathFunc);
			}
		}
	}

	protected void patchPluginXml() throws MojoExecutionException, MojoFailureException
	{
		PatchPluginXmlMojo.patchPluginXml(this);
	}

	private void writeRuntimeFile(ZipArchiveOutputStream zipStream, File file) throws IOException
	{
		ArchiveEntry entry = zipStream.createArchiveEntry(file, myId + "/lib/" + file.getName());

		zipStream.putArchiveEntry(entry);

		try (FileInputStream fileInputStream = new FileInputStream(file))
		{
			IOUtils.copy(fileInputStream, zipStream);
		}

		zipStream.closeArchiveEntry();
	}

	private void writeText(ZipArchiveOutputStream zipStream, String fileName, String text) throws IOException
	{
		ZipArchiveEntry entry = new ZipArchiveEntry(myId + "/lib/" + fileName);
		zipStream.putArchiveEntry(entry);

		IOUtils.copy(new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8)), zipStream);
		zipStream.closeArchiveEntry();
	}
}
