package consulo.maven.generating;

import org.apache.maven.model.Build;
import org.apache.maven.model.Resource;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugin.logging.SystemStreamLog;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.*;

/**
 * @author VISTALL
 * @since 27/11/2021
 */
@Mojo(name = "generate-icon-html", threadSafe = true, requiresDependencyResolution = ResolutionScope.NONE)
public class IconHtmlGenerateMojo extends AbstractIconGeneratorMojo
{
	private Map<String, Map<String, IconInfo>> myAllIcons = new TreeMap<>(Comparator.reverseOrder());

	@Override
	public void execute() throws MojoExecutionException, MojoFailureException
	{
		try
		{
			super.execute();

			generateHtml();
		}
		finally
		{
			myAllIcons.clear();
		}
	}

	private void generateHtml() throws MojoFailureException
	{
		File outputDirectory = new File(myMavenProject.getBuild().getDirectory());

		Set<String> allIconIds = new TreeSet<>();

		for(Map<String, IconInfo> map : myAllIcons.values())
		{
			allIconIds.addAll(map.keySet());
		}

		File f = new File(outputDirectory, "icon.html");

		StringBuilder builder = new StringBuilder();
		builder.append("<html>");
		builder.append("<head>");
		builder.append("<link rel=\"preconnect\" href=\"https://fonts.googleapis.com\">\n" +
				"<link rel=\"preconnect\" href=\"https://fonts.gstatic.com\" crossorigin>\n" +
				"<link href=\"https://fonts.googleapis.com/css2?family=Roboto&display=swap\" rel=\"stylesheet\">");
		builder.append("<style>");
		builder.append("html,body {font-family: 'Roboto', sans-serif; margin: 0px !important; padding: 0px !important}");
		builder.append("table,td {border-collapse: collapse; }");
		builder.append(".obsolete-png { text-decoration: line-through; }");
		builder.append(".icon-path { font-size: 12px; }");
		builder.append(".icon-path-dark { color: white; background: #282e33;}");
		builder.append(".theme-block {border: 1px solid gray}");
		builder.append(".main-table > table > tr > td {\n" +
				"  border: 1px solid black;\n" +
				"  border-collapse: collapse;\n" +
				"}");
		builder.append("</style>");
		builder.append("</head>");
		builder.append("<body style=\"width: 100%\">");

		builder.append("<table style=\"width: 100%\" class=\"main-table\">");
		builder.append("<thead>");
		builder.append("<tr>");
		builder.append("<td>Image ID</td>");
		builder.append("<td>W:H</td>");
		builder.append("<td>").append("Icon").append("</td>");
		builder.append("</tr>");
		builder.append("</thead>");

		for(String iconId : allIconIds)
		{
			boolean isSVG = false;
			String size = "??:??";

			for(Map<String, IconInfo> map : myAllIcons.values())
			{
				IconInfo iconInfo = map.get(iconId);
				if(iconInfo != null)
				{
					size = iconInfo.width + ":" + iconInfo.height;

					isSVG = iconInfo.isSVG;
				}
			}
			if(isSVG)
			{
				builder.append("<tr style=\"border: 1px solid gray; background: #baeeba\">");
			}
			else
			{
				builder.append("<tr style=\"border: 1px solid gray\">");
			}

			builder.append("<td>");
			builder.append(iconId);
			builder.append("</td>");
			builder.append("<td>").append(size).append("</td>");

			builder.append("<td>");

			for(Map.Entry<String, Map<String, IconInfo>> entry : myAllIcons.entrySet())
			{
				String themeId = entry.getKey();
				Map<String, IconInfo> icons = entry.getValue();

				boolean dark = themeId.equals("_dark");
				IconInfo iconInfo = icons.get(iconId);
				if(iconInfo != null)
				{
					if(dark)
					{
						builder.append("<div class=\"theme-block icon-path-dark\">");
					}
					else
					{
						builder.append("<div class=\"theme-block\">");
					}

					builder.append(themeId).append("<br>");

					for(File file : iconInfo.files)
					{
						appendIconRow(builder, file, iconInfo, dark);

						if(file.getName().endsWith(".png"))
						{
							File _2x = new File(file.getParentFile(), file.getName().replace(".png", "@2x.png"));
							if(_2x.exists())
							{
								appendIconRow(builder, _2x, iconInfo, dark);
							}
						}
						else if(file.getName().endsWith(".svg"))
						{
							File _2x = new File(file.getParentFile(), file.getName().replace(".svg", "@2x.svg"));
							if(_2x.exists())
							{
								appendIconRow(builder, _2x, iconInfo, dark);
							}
						}
					}
					builder.append("</div>");
				}
			}
			builder.append("</td>");

			builder.append("</tr>");
		}
		builder.append("</table>");

		builder.append("</body></html");
		try
		{
			Files.write(f.toPath(), builder.toString().getBytes(StandardCharsets.UTF_8), StandardOpenOption.TRUNCATE_EXISTING);
		}
		catch(IOException e)
		{
			throw new MojoFailureException(e.getMessage(), e);
		}
	}

	private void appendIconRow(StringBuilder builder, File file, IconInfo iconInfo, boolean dark)
	{
		int width = iconInfo.width;
		int height = iconInfo.height;

		if(iconInfo.height < 32 && iconInfo.width < 32)
		{
			width = width * 2;
			height = height * 2;
		}

		Path relativePath = iconInfo.sourcePath.relativize(file.toPath());

		builder.append("<table>");
		builder.append("<tr>");

		// image
		builder.append("<td>");
		builder.append("<img style=\"width: ");
		builder.append(width);
		builder.append("px;");
		builder.append("height: ");
		builder.append(height);
		builder.append("px;");
		builder.append("\" src=\"");
		builder.append(file.toURI());
		builder.append("\">");
		builder.append("</td>");

		// icon path
		builder.append("<td>");
		List<String> classes = new ArrayList<>();
		classes.add("icon-path");
		if(iconInfo.isSVG && file.getName().endsWith(".png"))
		{
			classes.add("obsolete-png");
		}
		if(dark)
		{
			classes.add("icon-path-dark");
		}

		builder.append("<span class=\"").append(String.join(" ", classes)).append("\">");

		builder.append(relativePath);
		builder.append("</span>");
		builder.append("</td>");

		builder.append("</tr>");
		builder.append("</table>");

	}

	@Override
	protected void generate(String themeId, String parentPackage, String name, String groupId, Log log, Map<String, IconInfo> icons, File outputDirectoryFile) throws IOException
	{
		Map<String, IconInfo> map = myAllIcons.computeIfAbsent(themeId, k -> new TreeMap<>());

		for(Map.Entry<String, IconInfo> entry : icons.entrySet())
		{
			String fieldName = entry.getKey();
			IconInfo iconInfo = entry.getValue();

			map.put(groupId + "." + fieldName, iconInfo);
		}
	}

	public static void main(String[] args) throws Exception
	{
		TEST_GENERATE = true;

		MavenProject mavenProject = new MavenProject();

		File projectDir = new File("W:\\_github.com\\consulo\\consulo\\modules\\base\\base-icon-library");
		Resource resource = new Resource();
		resource.setDirectory(new File(projectDir, "src\\main\\resources").getPath());
		Build build = new Build();
		build.addResource(resource);
		build.setOutputDirectory(new File(projectDir, "target").getAbsolutePath());
		build.setDirectory(new File(projectDir, "target").getAbsolutePath());
		mavenProject.setBuild(build);

		IconHtmlGenerateMojo mojo = new IconHtmlGenerateMojo();
		mojo.myMavenProject = mavenProject;
		mojo.setLog(new SystemStreamLog());

		mojo.execute();
	}
}
