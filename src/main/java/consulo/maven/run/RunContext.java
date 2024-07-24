package consulo.maven.run;

import consulo.maven.base.util.SystemInfo;
import org.apache.maven.project.MavenProject;

import java.io.File;

/**
 * @author VISTALL
 * @since 08-Jun-17
 */
public class RunContext {
    private final File mySandboxDirectory;
    private File myBuildDirectory;
    private boolean myForceBuildDirectory;

    private String myInnerBuildNumber;

    public RunContext(MavenProject project) {
        mySandboxDirectory = new File(project.getBasedir(), "sandbox");
        mySandboxDirectory.mkdirs();

        myBuildDirectory = new File(mySandboxDirectory, "platform");
    }

    public File getLibraryDirectory() {
        return new File(getPlatformDirectory(), "lib");
    }

    public File getDirectory(String name) {
        return new File(getPlatformDirectory(), name);
    }

    public File getPlatformDirectory() {
        final File platformDir;
        if (!myForceBuildDirectory && SystemInfo.getOS() == SystemInfo.OS.MACOS) {
            platformDir = new File(myBuildDirectory, "Consulo.app/Contents/platform");
        }
        else {
            platformDir = new File(myBuildDirectory, "Consulo/platform");
        }

        File[] files = platformDir.listFiles();

        if (files == null || files.length != 1) {
            throw new IllegalArgumentException();
        }
        return files[0];
    }

    public void setBuildDirectory(File buildDirectory) {
        myForceBuildDirectory = true;
        myBuildDirectory = buildDirectory;
    }

    public File getBuildDirectory() {
        return myBuildDirectory;
    }

    public File getSandboxDirectory() {
        return mySandboxDirectory;
    }

    public void findInnerBuildNumber() {
        File platformDirectory = getPlatformDirectory();

        String name = platformDirectory.getName();
        myInnerBuildNumber = name.replace("build", "");
    }

    public String getInnerBuildNumber() {
        return myInnerBuildNumber;
    }
}
