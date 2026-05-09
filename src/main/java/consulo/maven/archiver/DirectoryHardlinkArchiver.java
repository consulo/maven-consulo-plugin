package consulo.maven.archiver;

import org.codehaus.plexus.archiver.ArchiveEntry;
import org.codehaus.plexus.archiver.ArchiverException;
import org.codehaus.plexus.archiver.dir.DirectoryArchiver;
import org.codehaus.plexus.components.io.resources.PlexusIoFileResource;
import org.codehaus.plexus.components.io.resources.PlexusIoResource;

import javax.inject.Named;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * @author UNV
 * @since 2026-05-08
 */
@Named("dirHardlink")
public class DirectoryHardlinkArchiver extends DirectoryArchiver {
    private boolean myHardlinkSupported = true;
    private long myFilesLinked;

    @Override
    public void execute() throws ArchiverException, IOException {
        super.execute();

        getLogger().info(
            "{} file{} hard linked to {}",
            myFilesLinked,
            myFilesLinked > 0 ? "s" : "",
            getDestFile().getAbsolutePath()
        );
    }

    @Override
    protected void copyFile(ArchiveEntry entry, String vPath) throws ArchiverException, IOException {
        if (vPath.isEmpty()) {
            return;
        }
        PlexusIoResource in = entry.getResource();
        Path outFile = Path.of(vPath);

        if (!in.isDirectory() && myHardlinkSupported) {
            makeParentDirectories(outFile);

            if (in instanceof PlexusIoFileResource fileResource) {
                try {
                    Path inFile = fileResource.getFile().toPath();
                    if (Files.exists(outFile)) {
                        if (Files.isSameFile(inFile, outFile)) {
                            return;
                        }
                        Files.delete(outFile);
                    }
                    Files.createLink(outFile, inFile);
                    myFilesLinked++;
                    return;
                }
                catch (UnsupportedOperationException e) {
                    myHardlinkSupported = false;
                }
            }
        }

        super.copyFile(entry, vPath);
    }

    private static void makeParentDirectories(Path file) throws IOException {
        Path parent = file.getParent();
        if (!Files.exists(parent)) {
            Files.createDirectories(parent);
        }
    }

    @Override
    protected String getArchiveType() {
        return "directory-hardlink";
    }
}
