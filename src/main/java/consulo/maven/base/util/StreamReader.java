package consulo.maven.base.util;

/**
 * @author VISTALL
 * @since 24/05/2023
 */
@FunctionalInterface
public interface StreamReader<T, R>
{
	R read(T t) throws Exception;
}
