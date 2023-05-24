package consulo.maven.base.util;

import com.google.gson.Gson;
import consulo.maven.run.RunDesktopMojo;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.logging.Log;
import org.codehaus.plexus.util.IOUtil;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;

/**
 * @author VISTALL
 * @since 08-Jun-17
 */
public class HubApiUtil
{
	public static RepositoryNode requestRepositoryNodeInfo(String channel, String baseUrl, String id, String platformVersion, String version, Log log) throws MojoFailureException
	{
		if(platformVersion == null)
		{
			platformVersion = RunDesktopMojo.SNAPSHOT;
		}

		if(version == null)
		{
			version = RunDesktopMojo.SNAPSHOT;
		}

		String urlStr = String.format("%sinfo?id=%s&platformVersion=%s&version=%s&channel=%s", baseUrl, id, platformVersion, version, channel);

		log.debug("URL: " + urlStr);
		return connect(urlStr, inputStream ->
		{
			byte[] bytes = IOUtil.toByteArray(inputStream);
			return new Gson().fromJson(new String(bytes, StandardCharsets.UTF_8), RepositoryNode.class);
		});
	}

	public static void downloadRepositoryNode(String channel, String baseUrl, String id, String platformVersion, String version, File file, Log log) throws Exception
	{
		if(platformVersion == null)
		{
			platformVersion = RunDesktopMojo.SNAPSHOT;
		}

		if(version == null)
		{
			version = RunDesktopMojo.SNAPSHOT;
		}

		String urlStr = String.format("%sdownload?id=%s&platformVersion=%s&version=%s&channel=%s&noTracking=true&platformBuildSelect=true", baseUrl, id, platformVersion, version, channel);

		log.debug("URL: " + urlStr);
		try (FileOutputStream fileOutputStream = new FileOutputStream(file))
		{
			connect(urlStr, inputStream ->
			{
				IOUtil.copy(inputStream, fileOutputStream);
				return null;
			});
		}
	}

	private static <V> V connect(String urlStr, StreamReader<InputStream, V> inputStreamConsumer) throws MojoFailureException
	{
		URL url = null;
		try
		{
			url = new URL(urlStr);

			URLConnection urlConnection = url.openConnection();
			if(urlConnection instanceof HttpURLConnection)
			{
				// allow redirect
				((HttpURLConnection) urlConnection).setInstanceFollowRedirects(true);
			}

			try (InputStream inputStream = url.openStream())
			{
				return inputStreamConsumer.read(inputStream);
			}
		}
		catch(Exception e)
		{
			throw new MojoFailureException(e.getMessage(), e);
		}
	}
}