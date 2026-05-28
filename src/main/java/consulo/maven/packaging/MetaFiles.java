package consulo.maven.packaging;

import consulo.maven.packaging.processing.*;

/**
 * @author VISTALL
 * @author UNV
 * @since 2023-01-26
 */
public class MetaFiles extends JarProcessorGroup {
    public MetaFiles() {
        super(new JarIndexProcessor(), new IconJarProcessor(), new LocalizeJarProcessor(), new PluginMetaProcessor());
    }
}
