package consulo.maven.packaging;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Set;

import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;
import org.apache.commons.compress.utils.IOUtils;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.codehaus.plexus.util.FileUtils;

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
				String scope = dependencyArtifact.getScope();
				if(Artifact.SCOPE_COMPILE.equals(scope))
				{
					writeRuntimeFile(zipStream, dependencyArtifact.getFile());
				}
			}
		}
		catch(IOException e)
		{
			throw new MojoFailureException(e.getMessage(), e);
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
}
