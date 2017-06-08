package consulo.maven.run.util;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.HttpURLConnection;

import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import com.google.gson.Gson;
import consulo.maven.run.RunMojo;

/**
 * @author VISTALL
 * @since 08-Jun-17
 */
public class HubApiUtil
{
	public static RepositoryNode requestRepositoryNodeInfo(String channel, String baseUrl, String id, String platformVersion, String version)
	{
		if(platformVersion == null)
		{
			platformVersion = RunMojo.SNAPSHOT;
		}

		if(version == null)
		{
			version = RunMojo.SNAPSHOT;
		}

		try (CloseableHttpClient client = HttpClients.createMinimal())
		{
			String url = String.format("%sinfo?id=%s&platformVersion=%s&version=%s&channel=%s", baseUrl, id, platformVersion, version, channel);

			HttpGet httpGet = new HttpGet(url);

			return client.execute(httpGet, httpResponse ->
			{
				if(httpResponse.getStatusLine().getStatusCode() != HttpURLConnection.HTTP_OK)
				{
					return null;
				}
				return new Gson().fromJson(EntityUtils.toString(httpResponse.getEntity()), RepositoryNode.class);
			});
		}
		catch(IOException e)
		{
			return null;
		}
	}

	public static void downloadRepositoryNode(String channel, String baseUrl, String id, String platformVersion, String version, File file) throws Exception
	{
		if(platformVersion == null)
		{
			platformVersion = RunMojo.SNAPSHOT;
		}

		if(version == null)
		{
			version = RunMojo.SNAPSHOT;
		}

		try (FileOutputStream fileOutputStream = new FileOutputStream(file))
		{
			try (CloseableHttpClient client = HttpClients.createMinimal())
			{
				String url = String.format("%sdownload?id=%s&platformVersion=%s&version=%s&channel=%s&platformBuildSelect=true", baseUrl, id, platformVersion, version, channel);

				HttpGet httpGet = new HttpGet(url);

				client.execute(httpGet, httpResponse ->
				{
					if(httpResponse.getStatusLine().getStatusCode() != HttpURLConnection.HTTP_OK)
					{
						return null;
					}
					httpResponse.getEntity().writeTo(fileOutputStream);
					return null;
				});
			}
		}
	}
}