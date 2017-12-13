package consulo.maven.base.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;

import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.ArchiveInputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.apache.commons.compress.utils.IOUtils;

/**
 * @author VISTALL
 * @since 13-Dec-17
 */
public class ExtractUtil
{
	public static void extractTarGz(File tarFile, File directory) throws IOException
	{
		try (TarArchiveInputStream in = new TarArchiveInputStream(new GzipCompressorInputStream(new FileInputStream(tarFile))))
		{
			extractEntry(directory, in);
		}
	}

	public static void extractZip(File zipFile, File directory) throws IOException
	{
		try (ZipArchiveInputStream in = new ZipArchiveInputStream(new FileInputStream(zipFile)))
		{
			extractEntry(directory, in);
		}
	}

	private static void extractEntry(File directory, ArchiveInputStream in) throws IOException
	{
		ArchiveEntry entry = in.getNextEntry();
		while(entry != null)
		{
			if(entry.isDirectory())
			{
				entry = in.getNextEntry();
				continue;
			}
			File curfile = new File(directory, entry.getName());
			File parent = curfile.getParentFile();
			if(!parent.exists())
			{
				parent.mkdirs();
			}
			OutputStream out = new FileOutputStream(curfile);
			IOUtils.copy(in, out);
			out.close();
			entry = in.getNextEntry();
		}
	}
}
