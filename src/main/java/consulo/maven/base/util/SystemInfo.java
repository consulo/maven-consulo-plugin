package consulo.maven.base.util;

/**
 * @author VISTALL
 * @since 06-Jun-17
 */
public class SystemInfo
{
	public enum OS
	{
		WINDOWS("consulo.dist.windows.no.jre.zip"),
		MACOS("consulo.dist.mac64.no.jre"),
		LINUX("consulo.dist.linux.no.jre");

		private String myId;

		OS(String id)
		{
			myId = id;
		}

		public String getPlatformId()
		{
			return myId;
		}
	}

	public static OS getOS()
	{
		String property = System.getProperty("os.name").toLowerCase();
		if(property.startsWith("windows"))
		{
			return OS.WINDOWS;
		}
		else if(property.startsWith("mac os x"))
		{
			return OS.MACOS;
		}
		else
		{
			return OS.LINUX;
		}
	}
}
