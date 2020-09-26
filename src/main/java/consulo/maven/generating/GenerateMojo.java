package consulo.maven.generating;

import org.apache.maven.plugin.AbstractMojo;
import org.codehaus.plexus.util.StringUtils;

/**
 * @author VISTALL
 * @since 2020-09-26
 */
public abstract class GenerateMojo extends AbstractMojo
{
	public static boolean TEST_GENERATE = false;

	protected static String normalizeFirstChar(String text)
	{
		char c = text.charAt(0);
		if(c == '0')
		{
			return "zero" + text.substring(1, text.length());
		}
		else if(c == '1')
		{
			return "one" + text.substring(1, text.length());
		}
		else if(c == '2')
		{
			return "two" + text.substring(1, text.length());
		}
		return text;
	}

	protected static String captilizeByDot(String id)
	{
		String[] split = id.replace(" ", ".").split("\\.");

		StringBuilder builder = new StringBuilder();
		for(int i = 0; i < split.length; i++)
		{
			if(i != 0)
			{
				builder.append(StringUtils.capitalise(split[i]));
			}
			else
			{
				builder.append(split[i]);
			}
		}

		return builder.toString();
	}
}
