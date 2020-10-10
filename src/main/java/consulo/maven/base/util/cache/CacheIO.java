package consulo.maven.base.util.cache;

import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.IOUtil;

import java.io.*;

/**
 * @author VISTALL
 * @since 29-May-17
 */
public class CacheIO
{
	private static final int ourVersion = 3;

	private File myFile;

	private Cache myCache;

	public CacheIO(MavenProject mavenProject, String fileName)
	{
		myFile = new File(mavenProject.getBuild().getDirectory(), fileName);
	}

	public void read()
	{
		myCache = readImpl();
	}

	public void write()
	{
		if(myCache == null)
		{
			throw new IllegalArgumentException("#read() is not called");
		}

		Cache cache = myCache;
		myCache = null;

		File parentFile = myFile.getParentFile();
		parentFile.mkdirs();

		ObjectOutputStream stream = null;
		try
		{
			stream = new ObjectOutputStream(new FileOutputStream(myFile));
			stream.writeObject(cache);
		}
		catch(Exception ignored)
		{
		}
		finally
		{
			IOUtil.close(stream);
		}
	}

	private Cache readImpl()
	{
		if(!myFile.exists())
		{
			return new Cache(ourVersion);
		}

		ObjectInputStream stream = null;
		try
		{
			stream = new ObjectInputStream(new FileInputStream(myFile));
			Cache cache = (Cache) stream.readObject();
			// drop cache
			if(cache.getVersion() != ourVersion)
			{
				return new Cache(ourVersion);
			}
			return cache;
		}
		catch(Exception ignored)
		{
		}
		finally
		{
			IOUtil.close(stream);
		}
		return new Cache(ourVersion);
	}

	public boolean delete()
	{
		return myFile.exists() && myFile.delete();
	}

	public File getFile()
	{
		return myFile;
	}

	public void removeCacheEntry(File classFile)
	{
		myCache.removeCacheEntry(classFile);
	}

	public void putCacheEntry(File classFile)
	{
		myCache.putCacheEntry(classFile);
	}

	public boolean isUpToDate(File classFile)
	{
		return myCache.isUpToDate(classFile);
	}
}
