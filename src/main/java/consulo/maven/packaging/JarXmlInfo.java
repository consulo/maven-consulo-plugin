package consulo.maven.packaging;

/**
 * @author VISTALL
 * @since 26/01/2023
 */
public class JarXmlInfo
{
	private final String myPluginRequiresXml;
	private final String myPluginXml;

	public JarXmlInfo(String pluginRequiresXml, String pluginXml)
	{
		this.myPluginRequiresXml = pluginRequiresXml;
		this.myPluginXml = pluginXml;
	}

	public String getPluginXml()
	{
		return myPluginXml;
	}

	public String getPluginRequiresXml()
	{
		return myPluginRequiresXml;
	}
}
