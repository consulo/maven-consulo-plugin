package consulo.maven.generating;

import org.apache.maven.model.Build;
import org.apache.maven.model.Resource;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugin.logging.SystemStreamLog;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;

import java.io.File;
import java.io.IOException;
import java.util.Map;

/**
 * @author VISTALL
 * @since 27/11/2021
 */
@Mojo(name = "cleanup-png-icon", threadSafe = true, requiresDependencyResolution = ResolutionScope.NONE)
public class IconPngCleanupMojo extends AbstractIconGeneratorMojo
{
	@Override
	protected void generate(String themeId, String parentPackage, String name, String id, Log log, Map<String, IconInfo> icons, File outputDirectoryFile) throws IOException
	{
		for(IconInfo iconInfo : icons.values())
		{
			if(!iconInfo.isSVG)
			{
				continue;
			}

			for(File file : iconInfo.files)
			{
				if(file.getName().endsWith(".png"))
				{
					file.delete();

					File _2x = new File(file.getParentFile(), file.getName().replace(".png", "@2x.png"));

					if(_2x.exists())
					{
						_2x.delete();
					}
				}
			}
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

		IconPngCleanupMojo mojo = new IconPngCleanupMojo();
		mojo.myMavenProject = mavenProject;
		mojo.setLog(new SystemStreamLog());

		mojo.execute();
	}
}
