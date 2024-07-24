package consulo.maven.base.util.cache;

import java.io.File;
import java.io.Serializable;
import java.util.HashSet;

/**
 * @author VISTALL
 * @since 2019-11-30
 */
public class Cache implements Serializable {
    private static final long serialVersionUID = -8631961773974358488L;

    private HashSet<CacheEntry> myCacheEntries = new HashSet<>();
    private int myVersion;

    private Cache() {
    }

    public Cache(int version) {
        myVersion = version;
    }

    public boolean isUpToDate(File classFile) {
        CacheEntry cacheEntry = findCacheEntry(classFile);
        return cacheEntry != null && cacheEntry.getClassTimestamp() == classFile.lastModified();
    }

    public int getVersion() {
        return myVersion;
    }

    public CacheEntry findCacheEntry(File classFile) {
        if (myCacheEntries == null) {
            throw new IllegalArgumentException("#read() is not called");
        }

        for (CacheEntry cacheEntry : myCacheEntries) {
            if (cacheEntry.getClassFile().equals(classFile)) {
                return cacheEntry;
            }
        }
        return null;
    }

    public void removeCacheEntry(File classFile) {
        myCacheEntries.remove(new CacheEntry(classFile, -1));
    }

    public void putCacheEntry(File classFile) {
        removeCacheEntry(classFile);

        myCacheEntries.add(new CacheEntry(classFile, classFile.lastModified()));
    }
}
