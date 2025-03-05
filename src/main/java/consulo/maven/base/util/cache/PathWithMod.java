package consulo.maven.base.util.cache;

import jakarta.annotation.Nonnull;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

/**
 * @author VISTALL
 * @since 2025-03-05
 */
public class PathWithMod implements Serializable, Comparable<PathWithMod> {
    private static final long serialVersionUID = -4958612826243697962L;

    private String myFilePath;
    private long myModificationTime;

    private PathWithMod() {
    }

    public PathWithMod(Path file) throws IOException {
        myFilePath = file.toString();
        myModificationTime = Files.getLastModifiedTime(file).toMillis();
    }

    public PathWithMod(File file) {
        myFilePath = file.getPath();
        myModificationTime = file.lastModified();
    }

    public long getModificationTime() {
        return myModificationTime;
    }

    public String getFilePath() {
        return myFilePath;
    }

    @Override
    public int compareTo(@Nonnull PathWithMod o) {
        return myFilePath.compareTo(o.myFilePath);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        PathWithMod that = (PathWithMod) o;
        return myModificationTime == that.myModificationTime &&
            Objects.equals(myFilePath, that.myFilePath);
    }

    @Override
    public int hashCode() {
        return Objects.hash(myFilePath, myModificationTime);
    }

    @Override
    public String toString() {
        return "PathWithMod{" +
            "myFilePath='" + myFilePath + '\'' +
            ", myModificationTime=" + myModificationTime +
            '}';
    }
}
