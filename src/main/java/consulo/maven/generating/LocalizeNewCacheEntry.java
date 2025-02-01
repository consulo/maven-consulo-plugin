package consulo.maven.generating;

import consulo.compiler.apt.shared.generator.LocalizeGenerator;

import java.io.File;
import java.io.Serializable;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.TreeMap;

/**
 * @author VISTALL
 * @since 2025-02-01
 */
public class LocalizeNewCacheEntry implements Serializable {
    private static final long serialVersionUID = 1189682983719954569L;

    private Path myLocalizeFile;
    private TreeMap<Path, ArrayList<String>> mySubFiles;

    public LocalizeNewCacheEntry() {
    }

    public LocalizeNewCacheEntry(File file, List<LocalizeGenerator.SubFile> subFiles) {
        myLocalizeFile = file.toPath();
        mySubFiles = new TreeMap<>();

        for (LocalizeGenerator.SubFile subFile : subFiles) {
            mySubFiles.put(subFile.filePath(), new ArrayList<>(subFile.parts()));
        }
    }

    public TreeMap<Path, ArrayList<String>> getSubFiles() {
        return mySubFiles;
    }

    public Path getLocalizeFile() {
        return myLocalizeFile;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        LocalizeNewCacheEntry that = (LocalizeNewCacheEntry) o;
        return Objects.equals(myLocalizeFile, that.myLocalizeFile) &&
            Objects.equals(mySubFiles, that.mySubFiles);
    }

    @Override
    public int hashCode() {
        return Objects.hash(myLocalizeFile, mySubFiles);
    }

    @Override
    public String toString() {
        return "LocalizeNewCacheEntry{" +
            "myLocalizeFile=" + myLocalizeFile +
            ", mySubFiles=" + mySubFiles +
            '}';
    }
}
