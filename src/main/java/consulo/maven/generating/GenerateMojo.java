package consulo.maven.generating;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.shared.utils.StringUtils;

/**
 * @author VISTALL
 * @since 2020-09-26
 */
public abstract class GenerateMojo extends AbstractMojo {
    public static boolean TEST_GENERATE = false;

    protected static String captilizeByDot(String id) {
        String[] split = id.replace(" ", ".").split("\\.");

        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < split.length; i++) {
            if (i != 0) {
                builder.append(StringUtils.capitalise(split[i]));
            }
            else {
                builder.append(split[i]);
            }
        }

        return builder.toString();
    }
}
