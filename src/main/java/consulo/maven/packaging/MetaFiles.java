package consulo.maven.packaging;

import org.apache.maven.shared.utils.io.IOUtil;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;

/**
 * @author VISTALL
 * @since 26/01/2023
 */
public class MetaFiles
{
	public static final String[] META_FILES = {
			"META-INF/pluginIcon.svg",
			"META-INF/pluginIcon_dark.svg",
			"META-INF/plugin.xml",
	};

	private Map<String, String> myMetaData = new LinkedHashMap<>();

	public MetaFiles()
	{
	}

	public void readFromJar(File jarFile) throws IOException
	{
		try (JarFile jar = new JarFile(jarFile))
		{
			for(String metaFile : META_FILES)
			{
				ZipEntry entry = jar.getEntry(metaFile);
				if(entry != null)
				{
					try (InputStream stream = jar.getInputStream(entry))
					{
						myMetaData.put(metaFile, IOUtil.toString(stream));
					}
				}
			}
		}
	}

	public void forEachData(BiConsumer<String, String> consumer)
	{
		for(Map.Entry<String, String> entry : myMetaData.entrySet())
		{
			consumer.accept(entry.getKey(), entry.getValue());
		}
	}
}
