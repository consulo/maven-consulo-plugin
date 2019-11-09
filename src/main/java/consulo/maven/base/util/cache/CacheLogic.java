package consulo.maven.base.util.cache;

import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.IOUtil;

import java.io.*;
import java.util.HashSet;

/**
 * @author VISTALL
 * @since 29-May-17
 */
public class CacheLogic
{
	private File myFile;

	private HashSet<CacheEntry> myCacheEntries;

	public CacheLogic(MavenProject mavenProject, String fileName)
	{
		myFile = new File(mavenProject.getBuild().getDirectory(), fileName);
	}

	public void read()
	{
		myCacheEntries = readImpl();
	}

	public void write()
	{
		if(myCacheEntries == null)
		{
			throw new IllegalArgumentException("#read() is not called");
		}

		HashSet<CacheEntry> cacheEntries = myCacheEntries;
		myCacheEntries = null;

		File parentFile = myFile.getParentFile();
		parentFile.mkdirs();

		ObjectOutputStream stream = null;
		try
		{
			stream = new ObjectOutputStream(new FileOutputStream(myFile));
			stream.writeObject(cacheEntries);
		}
		catch(Exception ignored)
		{
		}
		finally
		{
			IOUtil.close(stream);
		}
	}

	public boolean isUpToDate(File classFile)
	{
		CacheEntry cacheEntry = findCacheEntry(classFile);
		return cacheEntry != null && cacheEntry.getClassTimestamp() == classFile.lastModified();
	}

	public CacheEntry findCacheEntry(File classFile)
	{
		if(myCacheEntries == null)
		{
			throw new IllegalArgumentException("#read() is not called");
		}

		for(CacheEntry cacheEntry : myCacheEntries)
		{
			if(cacheEntry.getClassFile().equals(classFile))
			{
				return cacheEntry;
			}
		}
		return null;
	}

	private HashSet<CacheEntry> readImpl()
	{
		if(!myFile.exists())
		{
			return new HashSet<CacheEntry>();
		}

		ObjectInputStream stream = null;
		try
		{
			stream = new ObjectInputStream(new FileInputStream(myFile));
			return (HashSet<CacheEntry>) stream.readObject();
		}
		catch(Exception ignored)
		{
		}
		finally
		{
			IOUtil.close(stream);
		}
		return new HashSet<CacheEntry>();
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
		myCacheEntries.remove(new CacheEntry(classFile, -1));
	}

	public void putCacheEntry(File classFile)
	{
		myCacheEntries.add(new CacheEntry(classFile, classFile.lastModified()));
	}
}
