package consulo.maven.run;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.*;

import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.apache.commons.compress.utils.IOUtils;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.FileUtils;
import consulo.maven.run.util.HubApiUtil;
import consulo.maven.run.util.RepositoryNode;
import consulo.maven.run.util.SystemInfo;

/**
 * @author VISTALL
 * @since 06-Jun-17
 * <p>
 * Threading impl from exec plugin on Apache 2
 */
@Mojo(name = "run", threadSafe = true, requiresDependencyResolution = ResolutionScope.TEST)
public class RunMojo extends AbstractMojo
{
	public static final String SNAPSHOT = "SNAPSHOT";

	private static List<Map.Entry<String, String>> ourBootArtifacts = new ArrayList<>();

	static
	{
		ourBootArtifacts.add(new AbstractMap.SimpleEntry<>("consulo", "consulo-desktop-bootstrap"));
		ourBootArtifacts.add(new AbstractMap.SimpleEntry<>("consulo", "consulo-extensions"));
		ourBootArtifacts.add(new AbstractMap.SimpleEntry<>("consulo", "consulo-util"));
		ourBootArtifacts.add(new AbstractMap.SimpleEntry<>("consulo", "consulo-util-rt"));
		ourBootArtifacts.add(new AbstractMap.SimpleEntry<>("consulo.internal", "jdom"));
		ourBootArtifacts.add(new AbstractMap.SimpleEntry<>("consulo.internal", "trove4j"));
		ourBootArtifacts.add(new AbstractMap.SimpleEntry<>("net.java.dev.jna", "jna"));
		ourBootArtifacts.add(new AbstractMap.SimpleEntry<>("net.java.dev.jna", "jna-platform"));
	}

	private static final String ourMainClass = "com.intellij.idea.Main";

	@Parameter(property = "buildNumber", defaultValue = SNAPSHOT)
	private String buildNumber;

	@Parameter(property = "buildDirectory", defaultValue = "")
	private String buildDirectory;

	@Parameter(property = "pluginDirectories")
	private List<String> pluginDirectories = new ArrayList<>();

	@Parameter(property = "buildDirectory", defaultValue = "https://hub.consulo.io/api/repository/")
	private String apiUrl;

	@Parameter(property = "repositoryChannel", defaultValue = "release")
	private String repositoryChannel;

	@Parameter(property = "arguments")
	private String[] arguments;

	@Parameter(defaultValue = "${project}", readonly = true)
	private MavenProject project;

	@Parameter(property = "exec.cleanupDaemonThreads", defaultValue = "true")
	protected boolean cleanupDaemonThreads;

	/**
	 * This defines the number of milliseconds to wait for daemon threads to quit following their interruption.<br/>
	 * This is only taken into account if {@link #cleanupDaemonThreads} is <code>true</code>. A value &lt;=0 means to
	 * not timeout (i.e. wait indefinitely for threads to finish). Following a timeout, a warning will be logged.
	 * <p>
	 * Note: properly coded threads <i>should</i> terminate upon interruption but some threads may prove problematic: as
	 * the VM does interrupt daemon threads, some code may not have been written to handle interruption properly. For
	 * example java.util.Timer is known to not handle interruptions in JDK &lt;= 1.6. So it is not possible for us to
	 * infinitely wait by default otherwise maven could hang. A sensible default value has been chosen, but this default
	 * value <i>may change</i> in the future based on user feedback.
	 * </p>
	 *
	 * @since 1.1-beta-1
	 */
	@Parameter(property = "exec.daemonThreadJoinTimeout", defaultValue = "15000")
	protected long daemonThreadJoinTimeout;

	/**
	 * Wether to call {@link Thread#stop()} following a timing out of waiting for an interrupted thread to finish. This
	 * is only taken into account if {@link #cleanupDaemonThreads} is <code>true</code> and the
	 * {@link #daemonThreadJoinTimeout} threshold has been reached for an uncooperative thread. If this is
	 * <code>false</code>, or if {@link Thread#stop()} fails to get the thread to stop, then a warning is logged and
	 * Maven will continue on while the affected threads (and related objects in memory) linger on. Consider setting
	 * this to <code>true</code> if you are invoking problematic code that you can't fix. An example is
	 * {@link java.util.Timer} which doesn't respond to interruption. To have <code>Timer</code> fixed, vote for
	 * <a href="http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=6336543">this bug</a>.
	 *
	 * @since 1.1-beta-1
	 */
	@Parameter(property = "exec.stopUnresponsiveDaemonThreads", defaultValue = "false")
	protected boolean stopUnresponsiveDaemonThreads;

	private Properties originalSystemProperties;

	@Override
	public void execute() throws MojoExecutionException, MojoFailureException
	{
		if(arguments == null)
		{
			arguments = new String[0];
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
				main.invoke(null, new Object[]{arguments});
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

		if(cleanupDaemonThreads)
		{
			terminateThreads(threadGroup);

			try
			{
				threadGroup.destroy();
			}
			catch(IllegalThreadStateException e)
			{
				getLog().warn("Couldn't destroy threadgroup " + threadGroup, e);
			}
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

	private void setSystemProperties(RunContext context)
	{
		originalSystemProperties = System.getProperties();
		for(Map.Entry<String, String> entry : getSystemProperties(context).entrySet())
		{
			String value = entry.getValue();
			System.setProperty(entry.getKey(), value == null ? "" : value);
		}
	}

	private Map<String, String> getSystemProperties(RunContext context)
	{
		Map<String, String> map = new HashMap<>();
		map.put("idea.home.path", context.getPlatformDirectory().getPath());
		map.put("consulo.in.sandbox", "true");
		// deprecated option
		map.put("idea.is.internal", "true");
		map.put("idea.config.path", context.getSandboxDirectory().getPath() + "/config");
		map.put("idea.system.path", context.getSandboxDirectory().getPath() + "/system");

		if(!pluginDirectories.isEmpty())
		{
			map.put("consulo.plugins.paths", String.join(File.pathSeparator, pluginDirectories));
			map.put("consulo.install.plugins.path", context.getSandboxDirectory().getPath() + "/config/plugins");
		}
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
		if(buildDirectory != null)
		{
			context.setBuildDirectory(new File(buildDirectory));
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

		if(buildNumber != null && buildNumber.equals(oldBuildNumber))
		{
			getLog().info("Consulo Buid: " + buildNumber + " - ok");
			return true;
		}

		getLog().info("Fetching platform info...");
		RepositoryNode repositoryNode = HubApiUtil.requestRepositoryNodeInfo(repositoryChannel, apiUrl, SystemInfo.getOS().getPlatformId(), buildNumber, null);
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
				File tmp = File.createTempFile("consulo_build", "tar.gz");

				HubApiUtil.downloadRepositoryNode(repositoryChannel, apiUrl, SystemInfo.getOS().getPlatformId(), buildNumber, null, tmp);

				if(oldBuildNumber != null)
				{
					getLog().info("Deleting old build");
				}

				FileUtils.deleteDirectory(platformDirectory);

				getLog().info("Extracting new build");

				extract(tmp, platformDirectory);

				FileUtils.fileWrite(buildNumberFile.getPath(), repositoryNode.version);

				return true;
			}
			catch(Exception e)
			{
				throw new MojoExecutionException(e.getMessage(), e);
			}
		}
	}

	public static void extract(File tarFile, File directory) throws IOException
	{
		try (TarArchiveInputStream in = new TarArchiveInputStream(new GzipCompressorInputStream(new FileInputStream(tarFile))))
		{
			ArchiveEntry entry = in.getNextEntry();
			while(entry != null)
			{
				if(entry.isDirectory())
				{
					entry = in.getNextEntry();
					continue;
				}
				File curfile = new File(directory, entry.getName());
				File parent = curfile.getParentFile();
				if(!parent.exists())
				{
					parent.mkdirs();
				}
				OutputStream out = new FileOutputStream(curfile);
				IOUtils.copy(in, out);
				out.close();
				entry = in.getNextEntry();
			}
		}
	}

	private MavenProject getTopProject()
	{
		MavenProject temp = project.getParent();
		if(temp == null)
		{
			return project;
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
