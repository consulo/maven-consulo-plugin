package consulo.maven.run;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.*;

/**
 * @author VISTALL
 * @since 06-Jun-17
 * <p>
 * Threading impl from exec plugin on Apache 2
 */
@SuppressWarnings("PlatformError")
public class RunDesktopMojo extends RunMojo {
    private Properties originalSystemProperties;

    @Override
    protected void run(String mainClassQualifiedName, RunContext context) throws MojoExecutionException, MojoFailureException {
        IsolatedThreadGroup threadGroup = new IsolatedThreadGroup(mainClassQualifiedName);
        Thread bootstrapThread = new Thread(threadGroup, () ->
        {
            try {
                Method main = Thread.currentThread().getContextClassLoader().loadClass(mainClassQualifiedName).getMethod("main", new Class[]{String[].class});
                if (!main.isAccessible()) {
                    getLog().debug("Setting accessibility to true in order to invoke main().");
                    main.setAccessible(true);
                }
                if (!Modifier.isStatic(main.getModifiers())) {
                    throw new MojoExecutionException("Can't call main(String[])-method because it is not static.");
                }
                main.invoke(null, new Object[]{execution.arguments});
            }
            catch (NoSuchMethodException e) { // just pass it on
                Thread.currentThread().getThreadGroup().uncaughtException(Thread.currentThread(), new Exception("The specified mainClass doesn't contain a main method with appropriate signature" +
                    "" + ".", e));
            }
            catch (InvocationTargetException e) { // use the cause if available to improve the plugin execution output
                Throwable exceptionToReport = e.getCause() != null ? e.getCause() : e;
                Thread.currentThread().getThreadGroup().uncaughtException(Thread.currentThread(), exceptionToReport);
            }
            catch (Exception e) { // just pass it on
                Thread.currentThread().getThreadGroup().uncaughtException(Thread.currentThread(), e);
            }
        }, mainClassQualifiedName + ".main()");
        bootstrapThread.setContextClassLoader(getClassLoader(context));
        setSystemProperties(context);

        bootstrapThread.start();
        joinNonDaemonThreads(threadGroup);
        terminateThreads(threadGroup);

        try {
            threadGroup.destroy();
        }
        catch (IllegalThreadStateException e) {
            getLog().warn("Couldn't destroy threadgroup " + threadGroup, e);
        }

        if (originalSystemProperties != null) {
            System.setProperties(originalSystemProperties);
        }

        synchronized (threadGroup) {
            if (threadGroup.uncaughtException != null) {
                throw new MojoExecutionException("An exception occured while executing the Java class. " + threadGroup.uncaughtException.getMessage(), threadGroup.uncaughtException);
            }
        }
    }

    private void setSystemProperties(RunContext context) throws MojoFailureException {
        originalSystemProperties = System.getProperties();
        for (Map.Entry<String, String> entry : getSystemProperties(context).entrySet()) {
            String value = entry.getValue();
            System.setProperty(entry.getKey(), value == null ? "" : value);
        }
    }

    private ClassLoader getClassLoader(RunContext context) throws MojoExecutionException {
        List<URL> classpathURLs = new ArrayList<>();
        addAdditionalClasspathElements(classpathURLs, context);
        return new URLClassLoader(classpathURLs.toArray(new URL[classpathURLs.size()]));
    }

    private void addAdditionalClasspathElements(List<URL> paths, RunContext context) throws MojoExecutionException {
        File bootDirectory = context.getDirectory("boot");
        if (!bootDirectory.exists()) {
            throw new MojoExecutionException("Boot directory is not exists");
        }

        addJars(bootDirectory, paths);

        File spiDir = new File(bootDirectory, "spi");
        if (spiDir.exists()) {
            addJars(spiDir, paths);
        }
    }

    private void addJars(File dir, List<URL> path) {
        for (File file : dir.listFiles()) {
            if (file.getName().endsWith(".jar")) {
                try {
                    path.add(file.toURI().toURL());
                }
                catch (MalformedURLException e) {
                    throw new RuntimeException(e);
                }
            }
        }
    }

    /**
     * a ThreadGroup to isolate execution and collect exceptions.
     */
    public class IsolatedThreadGroup extends ThreadGroup {
        public Throwable uncaughtException; // synchronize access to this

        public IsolatedThreadGroup(String name) {
            super(name);
        }

        public void uncaughtException(Thread thread, Throwable throwable) {
            if (throwable instanceof ThreadDeath) {
                return; // harmless
            }
            synchronized (this) {
                if (uncaughtException == null) // only remember the first one
                {
                    uncaughtException = throwable; // will be reported eventually
                }
            }
            getLog().warn(throwable);
        }
    }

    protected void joinNonDaemonThreads(ThreadGroup threadGroup) {
        boolean foundNonDaemon;
        do {
            foundNonDaemon = false;
            Collection<Thread> threads = getActiveThreads(threadGroup);
            for (Thread thread : threads) {
                if (thread.isDaemon()) {
                    continue;
                }
                foundNonDaemon = true; // try again; maybe more threads were created while we were busy
                joinThread(thread, 0);
            }
        }
        while (foundNonDaemon);
    }

    private void joinThread(Thread thread, long timeoutMsecs) {
        try {
            getLog().debug("joining on thread " + thread);
            thread.join(timeoutMsecs);
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt(); // good practice if don't throw
            getLog().warn("interrupted while joining against thread " + thread, e); // not expected!
        }
        if (thread.isAlive()) // generally abnormal
        {
            getLog().warn("thread " + thread + " was interrupted but is still alive after waiting at least " + timeoutMsecs + "msecs");
        }
    }

    protected void terminateThreads(ThreadGroup threadGroup) {
        long startTime = System.currentTimeMillis();
        Set<Thread> uncooperativeThreads = new HashSet<Thread>(); // these were not responsive to interruption
        for (Collection<Thread> threads = getActiveThreads(threadGroup); !threads.isEmpty(); threads = getActiveThreads(threadGroup), threads.removeAll(uncooperativeThreads)) {
            // Interrupt all threads we know about as of this instant (harmless if spuriously went dead (! isAlive())
            // or if something else interrupted it ( isInterrupted() ).
            for (Thread thread : threads) {
                getLog().debug("interrupting thread " + thread);
                thread.interrupt();
            }
            // Now join with a timeout and call stop() (assuming flags are set right)
            for (Thread thread : threads) {
                if (!thread.isAlive()) {
                    continue; // and, presumably it won't show up in getActiveThreads() next iteration
                }
                int daemonThreadJoinTimeout = 15000;
                if (daemonThreadJoinTimeout <= 0) {
                    joinThread(thread, 0); // waits until not alive; no timeout
                    continue;
                }
                long timeout = daemonThreadJoinTimeout - (System.currentTimeMillis() - startTime);
                if (timeout > 0) {
                    joinThread(thread, timeout);
                }
                if (!thread.isAlive()) {
                    continue;
                }
                uncooperativeThreads.add(thread); // ensure we don't process again
                boolean stopUnresponsiveDaemonThreads = false;
                if (stopUnresponsiveDaemonThreads) {
                    getLog().warn("thread " + thread + " will be Thread.stop()'ed");
                    thread.stop();
                }
                else {
                    getLog().warn("thread " + thread + " will linger despite being asked to die via interruption");
                }
            }
        }
        if (!uncooperativeThreads.isEmpty()) {
            getLog().warn("NOTE: " + uncooperativeThreads.size() + " thread(s) did not finish despite being asked to " + " via interruption. This is not a problem with exec:java, it is a problem " +
                "with the running code." + " Although not serious, it should be remedied.");
        }
        else {
            int activeCount = threadGroup.activeCount();
            if (activeCount != 0) {
                // TODO this may be nothing; continue on anyway; perhaps don't even log in future
                Thread[] threadsArray = new Thread[1];
                threadGroup.enumerate(threadsArray);
                getLog().debug("strange; " + activeCount + " thread(s) still active in the group " + threadGroup + " such as " + threadsArray[0]);
            }
        }
    }

    private Collection<Thread> getActiveThreads(ThreadGroup threadGroup) {
        Thread[] threads = new Thread[threadGroup.activeCount()];
        int numThreads = threadGroup.enumerate(threads);
        Collection<Thread> result = new ArrayList<Thread>(numThreads);
        for (int i = 0; i < threads.length && threads[i] != null; i++) {
            result.add(threads[i]);
        }
        return result; // note: result should be modifiable
    }
}
