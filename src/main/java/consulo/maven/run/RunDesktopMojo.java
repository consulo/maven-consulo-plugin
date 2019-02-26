package consulo.maven.run;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.*;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.FileUtils;
import consulo.maven.base.AbstractConsuloMojo;
import consulo.maven.base.util.ExtractUtil;
import consulo.maven.base.util.HubApiUtil;
import consulo.maven.base.util.RepositoryNode;
import consulo.maven.base.util.SystemInfo;
import consulo.maven.packaging.WorkspaceMojo;

/**
 * @author VISTALL
 * @since 06-Jun-17
 * <p>
 * Threading impl from exec plugin on Apache 2
 */
@Mojo(name = "run-desktop", threadSafe = true, requiresDependencyResolution = ResolutionScope.COMPILE_PLUS_RUNTIME, defaultPhase = LifecyclePhase.PACKAGE)
public class RunDesktopMojo extends AbstractConsuloMojo
{
	private static List<Map.Entry<String, String>> ourBootArtifacts = new ArrayList<>();

	static
	{
		ourBootArtifacts.add(new AbstractMap.SimpleEntry<>("consulo", "consulo-desktop-bootstrap"));
		ourBootArtifacts.add(new AbstractMap.SimpleEntry<>("consulo", "consulo-util"));
		ourBootArtifacts.add(new AbstractMap.SimpleEntry<>("consulo", "consulo-util-rt"));
		ourBootArtifacts.add(new AbstractMap.SimpleEntry<>("consulo.internal", "jdom"));
		ourBootArtifacts.add(new AbstractMap.SimpleEntry<>("consulo.internal", "trove4j"));
		ourBootArtifacts.add(new AbstractMap.SimpleEntry<>("net.java.dev.jna", "jna"));
		ourBootArtifacts.add(new AbstractMap.SimpleEntry<>("net.java.dev.jna", "jna-platform"));
	}

	private static final String ourMainClass = "com.intellij.idea.Main";

	public static class ExecutionConfig
	{
		@Parameter(property = "buildNumber", defaultValue = SNAPSHOT)
		private String buildNumber = SNAPSHOT;

		@Parameter(property = "buildDirectory", defaultValue = "")
		private String buildDirectory;

		@Parameter(property = "useDefaultWorkspaceDirectory", defaultValue = "true")
		private boolean useDefaultWorkspaceDirectory = true;

		@Parameter(property = "pluginDirectories")
		private List<String> pluginDirectories = new ArrayList<>();

		@Parameter(property = "arguments")
		private String[] arguments = new String[0];
	}

	@Parameter(property = "execution")
	private ExecutionConfig execution = new ExecutionConfig();

	private Properties originalSystemProperties;

	@Override
	public void execute() throws MojoExecutionException, MojoFailureException
	{
		if(execution.arguments == null)
		{
			execution.arguments = new String[0];
		}

		RunContext context = new RunContext(getTopProject());

		if(!validateBuild(context))
		{
			return;
		}

		context.findInnerBuildNumber();

		IsolatedThreadGroup threadGroup = new IsolatedThreadGroup(ourMainClass);
		Thread bootstrapThread = new Thread(threadGroup, () ->
		{
			try
			{
				Method main = Thread.currentThread().getContextClassLoader().loadClass(ourMainClass).getMethod("main", new Class[]{String[].class});
				if(!main.isAccessible())
				{
					getLog().debug("Setting accessibility to true in order to invoke main().");
					main.setAccessible(true);
				}
				if(!Modifier.isStatic(main.getModifiers()))
				{
					throw new MojoExecutionException("Can't call main(String[])-method because it is not static.");
				}
				main.invoke(null, new Object[]{execution.arguments});
			}
			catch(NoSuchMethodException e)
			{ // just pass it on
				Thread.currentThread().getThreadGroup().uncaughtException(Thread.currentThread(), new Exception("The specified mainClass doesn't contain a main method with appropriate signature" +
						"" + ".", e));
			}
			catch(InvocationTargetException e)
			{ // use the cause if available to improve the plugin execution output
				Throwable exceptionToReport = e.getCause() != null ? e.getCause() : e;
				Thread.currentThread().getThreadGroup().uncaughtException(Thread.currentThread(), exceptionToReport);
			}
			catch(Exception e)
			{ // just pass it on
				Thread.currentThread().getThreadGroup().uncaughtException(Thread.currentThread(), e);
			}
		}, ourMainClass + ".main()");
		bootstrapThread.setContextClassLoader(getClassLoader(context));
		setSystemProperties(context);

		bootstrapThread.start();
		joinNonDaemonThreads(threadGroup);
		terminateThreads(threadGroup);

		try
		{
			threadGroup.destroy();
		}
		catch(IllegalThreadStateException e)
		{
			getLog().warn("Couldn't destroy threadgroup " + threadGroup, e);
		}

		if(originalSystemProperties != null)
		{
			System.setProperties(originalSystemProperties);
		}

		synchronized(threadGroup)
		{
			if(threadGroup.uncaughtException != null)
			{
				throw new MojoExecutionException("An exception occured while executing the Java class. " + threadGroup.uncaughtException.getMessage(), threadGroup.uncaughtException);
			}
		}
	}

	private void setSystemProperties(RunContext context) throws MojoFailureException
	{
		originalSystemProperties = System.getProperties();
		for(Map.Entry<String, String> entry : getSystemProperties(context).entrySet())
		{
			String value = entry.getValue();
			System.setProperty(entry.getKey(), value == null ? "" : value);
		}
	}

	private Map<String, String> getSystemProperties(RunContext context) throws MojoFailureException
	{
		Map<String, String> map = new HashMap<>();
		map.put("idea.home.path", context.getPlatformDirectory().getPath());
		map.put("consulo.in.sandbox", "true");  // sandbox mode
		map.put("consulo.maven.console.log", "true"); // redirect file log to console
		// deprecated option
		map.put("idea.is.internal", "true");
		map.put("idea.config.path", context.getSandboxDirectory().getPath() + "/config");
		map.put("idea.system.path", context.getSandboxDirectory().getPath() + "/system");

		List<String> pluginPaths = new ArrayList<>();

		if(execution.useDefaultWorkspaceDirectory)
		{
			File targetDirectory = WorkspaceMojo.getExtractedDirectory(myProject);

			if(targetDirectory.exists())
			{
				pluginPaths.add(targetDirectory.getPath());
			}
		}

		if(!myDependencies.isEmpty())
		{
			File dependenciesDirectory = WorkspaceMojo.getDependenciesDirectory(myProject);
			pluginPaths.add(dependenciesDirectory.getPath());
		}

		for(String pluginDirectory : execution.pluginDirectories)
		{
			File dir = new File(pluginDirectory);
			if(dir.exists())
			{
				try
				{
					pluginPaths.add(dir.getCanonicalPath());
				}
				catch(IOException e)
				{
					throw new MojoFailureException(e.getMessage(), e);
				}
			}
		}

		pluginPaths.add(context.getSandboxDirectory().getPath() + "/config/plugins");

		map.put("consulo.plugins.paths", String.join(File.pathSeparator, pluginPaths));
		map.put("consulo.install.plugins.path", context.getSandboxDirectory().getPath() + "/config/plugins");
		return map;
	}

	private ClassLoader getClassLoader(RunContext context) throws MojoExecutionException
	{
		List<URL> classpathURLs = new ArrayList<>();
		addAdditionalClasspathElements(classpathURLs, context);
		return new URLClassLoader(classpathURLs.toArray(new URL[classpathURLs.size()]));
	}

	private void addAdditionalClasspathElements(List<URL> path, RunContext context) throws MojoExecutionException
	{
		File libraryDirectory = context.getLibraryDirectory();

		for(Map.Entry<String, String> bootArtifact : ourBootArtifacts)
		{
			File file = new File(libraryDirectory, bootArtifact.getValue() + ".jar");
			if(file.exists())
			{
				try
				{
					path.add(file.toURI().toURL());
				}
				catch(MalformedURLException e)
				{
					throw new RuntimeException(e);
				}
			}
			else
			{
				throw new MojoExecutionException("File " + file.getPath() + " is not exists");
			}
		}
	}

	private boolean validateBuild(RunContext context) throws MojoExecutionException
	{
		if(execution.buildDirectory != null)
		{
			context.setBuildDirectory(new File(execution.buildDirectory));
			return true;
		}

		File platformDirectory = context.getBuildDirectory();
		File buildNumberFile = new File(platformDirectory, "build.txt");

		String oldBuildNumber = null;
		if(platformDirectory.exists())
		{
			if(buildNumberFile.exists())
			{
				try
				{
					oldBuildNumber = FileUtils.fileRead(buildNumberFile);
				}
				catch(IOException ignored)
				{
				}
			}
		}

		if(execution.buildNumber != null && execution.buildNumber.equals(oldBuildNumber))
		{
			getLog().info("Consulo Build: " + execution.buildNumber + " - ok");
			return true;
		}

		getLog().info("Fetching platform info...");
		RepositoryNode repositoryNode = HubApiUtil.requestRepositoryNodeInfo(myRepositoryChannel, myApiUrl, SystemInfo.getOS().getPlatformId(), execution.buildNumber, null);
		if(repositoryNode == null)
		{
			if(oldBuildNumber == null)
			{
				getLog().error("No connection and no old Consulo build.");
				return false;
			}
			else
			{
				getLog().info("No connection - using old Consulo build");
				return true;
			}
		}
		else
		{
			if(Objects.equals(repositoryNode.version, oldBuildNumber))
			{
				getLog().info("Consulo build is not changed: " + oldBuildNumber);
				return true;
			}
			else if(oldBuildNumber != null)
			{
				getLog().info("Consulo build is changed. Old build: " + oldBuildNumber + ", downloading new build: " + repositoryNode.version);
			}
			else
			{
				getLog().info("No old build, downloading Consulo build: " + repositoryNode.version);
			}

			try
			{
				File tempFile = File.createTempFile("consulo_build", "tar.gz");
				tempFile.deleteOnExit();

				HubApiUtil.downloadRepositoryNode(myRepositoryChannel, myApiUrl, SystemInfo.getOS().getPlatformId(), execution.buildNumber, null, tempFile);

				if(oldBuildNumber != null)
				{
					getLog().info("Deleting old build");
				}

				FileUtils.deleteDirectory(platformDirectory);

				getLog().info("Extracting new build");

				ExtractUtil.extractTarGz(tempFile, platformDirectory);

				FileUtils.fileWrite(buildNumberFile.getPath(), repositoryNode.version);

				return true;
			}
			catch(Exception e)
			{
				throw new MojoExecutionException(e.getMessage(), e);
			}
		}
	}

	private MavenProject getTopProject()
	{
		MavenProject temp = myProject.getParent();
		if(temp == null)
		{
			return myProject;
		}

		while(true)
		{
			MavenProject parent = temp.getParent();
			if(parent == null)
			{
				break;
			}
			temp = parent;
		}

		return temp;
	}

	/**
	 * a ThreadGroup to isolate execution and collect exceptions.
	 */
	public class IsolatedThreadGroup extends ThreadGroup
	{
		public Throwable uncaughtException; // synchronize access to this

		public IsolatedThreadGroup(String name)
		{
			super(name);
		}

		public void uncaughtException(Thread thread, Throwable throwable)
		{
			if(throwable instanceof ThreadDeath)
			{
				return; // harmless
			}
			synchronized(this)
			{
				if(uncaughtException == null) // only remember the first one
				{
					uncaughtException = throwable; // will be reported eventually
				}
			}
			getLog().warn(throwable);
		}
	}

	protected void joinNonDaemonThreads(ThreadGroup threadGroup)
	{
		boolean foundNonDaemon;
		do
		{
			foundNonDaemon = false;
			Collection<Thread> threads = getActiveThreads(threadGroup);
			for(Thread thread : threads)
			{
				if(thread.isDaemon())
				{
					continue;
				}
				foundNonDaemon = true; // try again; maybe more threads were created while we were busy
				joinThread(thread, 0);
			}
		}
		while(foundNonDaemon);
	}

	private void joinThread(Thread thread, long timeoutMsecs)
	{
		try
		{
			getLog().debug("joining on thread " + thread);
			thread.join(timeoutMsecs);
		}
		catch(InterruptedException e)
		{
			Thread.currentThread().interrupt(); // good practice if don't throw
			getLog().warn("interrupted while joining against thread " + thread, e); // not expected!
		}
		if(thread.isAlive()) // generally abnormal
		{
			getLog().warn("thread " + thread + " was interrupted but is still alive after waiting at least " + timeoutMsecs + "msecs");
		}
	}

	protected void terminateThreads(ThreadGroup threadGroup)
	{
		long startTime = System.currentTimeMillis();
		Set<Thread> uncooperativeThreads = new HashSet<Thread>(); // these were not responsive to interruption
		for(Collection<Thread> threads = getActiveThreads(threadGroup); !threads.isEmpty(); threads = getActiveThreads(threadGroup), threads.removeAll(uncooperativeThreads))
		{
			// Interrupt all threads we know about as of this instant (harmless if spuriously went dead (! isAlive())
			// or if something else interrupted it ( isInterrupted() ).
			for(Thread thread : threads)
			{
				getLog().debug("interrupting thread " + thread);
				thread.interrupt();
			}
			// Now join with a timeout and call stop() (assuming flags are set right)
			for(Thread thread : threads)
			{
				if(!thread.isAlive())
				{
					continue; // and, presumably it won't show up in getActiveThreads() next iteration
				}
				int daemonThreadJoinTimeout = 15000;
				if(daemonThreadJoinTimeout <= 0)
				{
					joinThread(thread, 0); // waits until not alive; no timeout
					continue;
				}
				long timeout = daemonThreadJoinTimeout - (System.currentTimeMillis() - startTime);
				if(timeout > 0)
				{
					joinThread(thread, timeout);
				}
				if(!thread.isAlive())
				{
					continue;
				}
				uncooperativeThreads.add(thread); // ensure we don't process again
				boolean stopUnresponsiveDaemonThreads = false;
				if(stopUnresponsiveDaemonThreads)
				{
					getLog().warn("thread " + thread + " will be Thread.stop()'ed");
					thread.stop();
				}
				else
				{
					getLog().warn("thread " + thread + " will linger despite being asked to die via interruption");
				}
			}
		}
		if(!uncooperativeThreads.isEmpty())
		{
			getLog().warn("NOTE: " + uncooperativeThreads.size() + " thread(s) did not finish despite being asked to " + " via interruption. This is not a problem with exec:java, it is a problem " +
					"with the running code." + " Although not serious, it should be remedied.");
		}
		else
		{
			int activeCount = threadGroup.activeCount();
			if(activeCount != 0)
			{
				// TODO this may be nothing; continue on anyway; perhaps don't even log in future
				Thread[] threadsArray = new Thread[1];
				threadGroup.enumerate(threadsArray);
				getLog().debug("strange; " + activeCount + " thread(s) still active in the group " + threadGroup + " such as " + threadsArray[0]);
			}
		}
	}

	private Collection<Thread> getActiveThreads(ThreadGroup threadGroup)
	{
		Thread[] threads = new Thread[threadGroup.activeCount()];
		int numThreads = threadGroup.enumerate(threads);
		Collection<Thread> result = new ArrayList<Thread>(numThreads);
		for(int i = 0; i < threads.length && threads[i] != null; i++)
		{
			result.add(threads[i]);
		}
		return result; // note: result should be modifiable
	}
}
