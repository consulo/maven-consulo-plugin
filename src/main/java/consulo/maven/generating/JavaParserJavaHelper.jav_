package consulo.maven.generating;

import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.intellij.grammar.java.JavaHelper;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Modifier;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.BodyDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.utils.SourceRoot;
import com.intellij.psi.NavigatablePsiElement;

/**
 * @author VISTALL
 * @since 2018-06-18
 */
class JavaParserJavaHelper extends JavaHelper
{
	static class CompilationUnitDelegate extends MyElement<CompilationUnit>
	{
		protected CompilationUnitDelegate(CompilationUnit delegate)
		{
			super(delegate);
		}
	}

	static class MethodDelegate extends MyElement<MethodDeclaration>
	{
		protected MethodDelegate(MethodDeclaration delegate)
		{
			super(delegate);
		}

		@Override
		public String getName()
		{
			return getDelegate().getNameAsString();
		}
	}

	private SourceRoot mySourceRoot;

	private Map<String, CompilationUnit> myUnits = new HashMap<>();

	public JavaParserJavaHelper(String sourceDirectory, String directoryToGenerate)
	{
		mySourceRoot = new SourceRoot(Paths.get(sourceDirectory));
	}

	@Nullable
	@Override
	public NavigatablePsiElement findClass(@Nullable String className)
	{
		assert className != null;

		CompilationUnit compilationUnit = myUnits.computeIfAbsent(className, s -> mySourceRoot.parse("", s.replace(".", "/") + ".java"));

		return new CompilationUnitDelegate(compilationUnit);
	}

	@Nonnull
	@Override
	public List<NavigatablePsiElement> findClassMethods(@Nullable String className, @Nonnull MethodType methodType, @Nullable String methodName, int paramCount, String... paramTypes)
	{
		if(className == null)
		{
			return Collections.emptyList();
		}

		System.out.println("findClassMethods " + methodName + " " + methodType + " " + paramCount);

		CompilationUnit compilationUnit = myUnits.get(className);
		if(compilationUnit == null)
		{
			System.out.println("Class not found: " + className);
			return Collections.emptyList();
		}

		List<NavigatablePsiElement> methodDelegates = new ArrayList<>();

		TypeDeclaration<?> declaration = compilationUnit.getTypes().iterator().next();
		NodeList<BodyDeclaration<?>> members = declaration.getMembers();
		loop:
		for(BodyDeclaration<?> member : members)
		{
			if(member instanceof MethodDeclaration)
			{
				switch(methodType)
				{
					case STATIC:
						if(!((MethodDeclaration) member).getModifiers().contains(Modifier.STATIC))
						{
							continue loop;
						}
						break;
					case INSTANCE:
						if(((MethodDeclaration) member).getModifiers().contains(Modifier.STATIC))
						{
							continue loop;
						}
						break;
					case CONSTRUCTOR:
						break;
				}

				if(methodName != null)
				{
					String nameAsString = ((MethodDeclaration) member).getNameAsString();
					if(!methodName.equals(nameAsString))
					{
						continue loop;
					}
				}
				methodDelegates.add(new MethodDelegate((MethodDeclaration) member));
			}
		}

		System.out.println("return " + methodDelegates.size());
		return methodDelegates;
	}

	@Nonnull
	@Override
	public List<String> getMethodTypes(@Nullable NavigatablePsiElement method)
	{
		if(!(method instanceof MethodDelegate))
		{
			return Collections.emptyList();
		}

		MethodDeclaration delegate = ((MethodDelegate) method).getDelegate();

		List<String> list = new ArrayList<>();
		list.add(delegate.getTypeAsString());

		for(Parameter parameter : delegate.getParameters())
		{
			list.add(parameter.getTypeAsString());
			list.add(parameter.getNameAsString());
		}

		System.out.println("return " + list + " " + method.getName());
		return list;
	}
}
