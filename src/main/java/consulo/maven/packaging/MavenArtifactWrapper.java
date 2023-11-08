package consulo.maven.packaging;

import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;
import org.apache.commons.compress.utils.IOUtils;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.shared.utils.io.FileUtils;

import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;

/**
 * @author VISTALL
 * @since 25/05/2023
 */
public class MavenArtifactWrapper
{
	private final File myArtifactFile;
	private final Artifact myArtifact;

	public MavenArtifactWrapper(File artifactFile, Artifact artifact)
	{
		myArtifactFile = artifactFile;
		myArtifact = artifact;
	}

	public String getArtifactName()
	{
		return myArtifact.getArtifactId() + "-" + myArtifact.getBaseVersion() + ".jar";
	}

	public File copyTo(File directory, @Nullable String fileName) throws IOException
	{
		if(fileName == null)
		{
			fileName = getArtifactName();
		}

		if(myArtifactFile.isDirectory())
		{
			return buildJarArtifact(directory, fileName);
		}
		else
		{
			File destination = new File(directory, fileName);
			FileUtils.copyFile(myArtifactFile, destination);
			return destination;
		}
	}

	public File buildJarArtifact(File libDirectory, String fileName) throws IOException
	{
		File jarArchiveFile = new File(libDirectory, fileName);

		try (ZipArchiveOutputStream zipStream = new ZipArchiveOutputStream(jarArchiveFile))
		{
			Path classesDir = myArtifactFile.toPath();
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

		return jarArchiveFile;
	}
}
