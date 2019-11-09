package consulo.maven.base.util.cache;

import java.io.File;
import java.io.Serializable;

/**
 * @author VISTALL
 * @since 29-May-17
 */
public class CacheEntry implements Serializable
{
	private static final long serialVersionUID = -2356544437139790239L;

	private File myClassFile;
	private long myClassTimestamp;

	private CacheEntry()
	{
	}

	public CacheEntry(File classFile, long classTimestamp)
	{
		myClassFile = classFile;
		myClassTimestamp = classTimestamp;
	}

	public File getClassFile()
	{
		return myClassFile;
	}

	public long getClassTimestamp()
	{
		return myClassTimestamp;
	}

	@Override
	public boolean equals(Object o)
	{
		if(this == o)
		{
			return true;
		}
		if(o == null || getClass() != o.getClass())
		{
			return false;
		}

		CacheEntry that = (CacheEntry) o;

		if(myClassFile != null ? !myClassFile.equals(that.myClassFile) : that.myClassFile != null)
		{
			return false;
		}

		return true;
	}

	@Override
	public int hashCode()
	{
		int result = 0;
		result = 31 * result + (myClassFile != null ? myClassFile.hashCode() : 0);
		return result;
	}
}
