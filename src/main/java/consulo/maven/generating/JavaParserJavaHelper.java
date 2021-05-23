package consulo.maven.generating;

import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Modifier;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.*;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.Type;
import com.github.javaparser.ast.type.TypeParameter;
import com.github.javaparser.resolution.types.ResolvedReferenceType;
import com.github.javaparser.resolution.types.ResolvedType;
import com.github.javaparser.symbolsolver.JavaSymbolSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.JavaParserTypeSolver;
import com.github.javaparser.utils.SourceRoot;
import com.google.common.collect.BiMap;
import com.intellij.codeInsight.highlighting.ReadWriteAccessDetector;
import com.intellij.navigation.ItemPresentation;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.Trinity;
import com.intellij.psi.*;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.stubs.IStubElementType;
import org.intellij.grammar.java.JavaHelper;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.nio.file.Paths;
import java.util.*;

/**
 * @author VISTALL
 * @since 2018-06-18
 */
class JavaParserJavaHelper extends JavaHelper
{
	static class TypeDeclarationDelegate extends MyElement<TypeDeclaration>
	{
		protected TypeDeclarationDelegate(TypeDeclaration delegate)
		{
			super(delegate);
		}
	}

	static class MethodDelegate extends MyElement<CallableDeclaration>
	{
		private final String myDeclarationQName;

		protected MethodDelegate(CallableDeclaration delegate, String declarationQName)
		{
			super(delegate);
			myDeclarationQName = declarationQName;
		}

		@Override
		public String getName()
		{
			return getDelegate().getNameAsString();
		}
	}

	private SourceRoot mySourceRoot;

	private SourceRoot myGeneratedRoot;

	private Map<String, TypeDeclaration> myUnits = new HashMap<>();
	private BiMap<String, String> myRuleClassNames;
	private Map<String, String> myBaseClassNames;

	public JavaParserJavaHelper(String sourceDirectory, String directoryToGenerate)
	{
		ParserConfiguration configuration = new ParserConfiguration();
		configuration.setSymbolResolver(new JavaSymbolSolver(new JavaParserTypeSolver(sourceDirectory)));

		mySourceRoot = new SourceRoot(Paths.get(sourceDirectory), configuration);
		myGeneratedRoot = new SourceRoot(Paths.get(directoryToGenerate), configuration);
	}

	@Nullable
	@Override
	public NavigatablePsiElement findClass(@Nullable String className)
	{
		NavigatablePsiElement impl = findClassImpl(className, mySourceRoot);
		if(impl != null)
		{
			return impl;
		}
		impl = findClassImpl(className, myGeneratedRoot);
		if(impl != null)
		{
			return impl;
		}
		//System.out.println("class not found " + className);
		return impl;
	}

	private NavigatablePsiElement findClassImpl(@Nullable String className, SourceRoot sourceRoot)
	{
		assert className != null;

		TypeDeclaration typeDeclaration = myUnits.get(className);
		if(typeDeclaration != null)
		{
			return new TypeDeclarationDelegate(typeDeclaration);
		}

		try
		{
			CompilationUnit unit = sourceRoot.parse("", className.replace(".", "/") + ".java");
			for(TypeDeclaration<?> declaration : unit.getTypes())
			{
				if(declaration.getFullyQualifiedName().get().equals(className))
				{
					typeDeclaration = declaration;
					break;
				}
			}
		}
		catch(Exception e)
		{
		}

		if(typeDeclaration == null)
		{
			return null;
		}

		myUnits.put(className, typeDeclaration);

		return new TypeDeclarationDelegate(typeDeclaration);
	}

	@Nonnull
	@Override
	public List<NavigatablePsiElement> findClassMethods(@Nullable String className, @Nonnull MethodType methodType, @Nullable String methodName, int paramCount, String... paramTypes)
	{
		if(className == null)
		{
			return Collections.emptyList();
		}

		NavigatablePsiElement element = findClass(className);
		TypeDeclaration declaration = element instanceof TypeDeclarationDelegate ? ((TypeDeclarationDelegate) element).getDelegate() : null;
		if(declaration == null)
		{
			return Collections.emptyList();
		}

		List<NavigatablePsiElement> methodDelegates = new ArrayList<>();

		NodeList<BodyDeclaration<?>> members = declaration.getMembers();
		loop:
		for(BodyDeclaration<?> member : members)
		{
			if(member instanceof MethodDeclaration && methodType != MethodType.CONSTRUCTOR)
			{
				switch(methodType)
				{
					case STATIC:
						if(!((MethodDeclaration) member).getModifiers().contains(Modifier.staticModifier()))
						{
							continue loop;
						}
						break;
					case INSTANCE:
						if(((MethodDeclaration) member).getModifiers().contains(Modifier.staticModifier()))
						{
							continue loop;
						}
						break;
				}

				if(!acceptsName(methodName, ((MethodDeclaration) member).getNameAsString()))
				{
					continue;
				}

				if(!acceptMethod((MethodDeclaration) member, paramCount, paramTypes))
				{
					continue;
				}

				methodDelegates.add(new MethodDelegate((MethodDeclaration) member, className));
			}
			else if(member instanceof ConstructorDeclaration && methodType == MethodType.CONSTRUCTOR)
			{
				if(!acceptMethod((ConstructorDeclaration) member, paramCount, paramTypes))
				{
					continue;
				}

				methodDelegates.add(new MethodDelegate((ConstructorDeclaration) member, className));
			}
		}

		return methodDelegates;
	}

	private boolean acceptMethod(CallableDeclaration<?> callableDeclaration, int paramsCount, String[] paramTypes)
	{
		NodeList<Parameter> parameters = callableDeclaration.getParameters();
		if(paramsCount > 0 && parameters.size() != paramsCount)
		{
			return false;
		}

		if(parameters.size() == 0)
		{
			return true;
		}

		if(parameters.size() < paramTypes.length)
		{
			return false;
		}

		for(int i = 0; i < paramTypes.length; i++)
		{
			String paramType = paramTypes[i];
			Parameter parameter = parameters.get(i);

			String t = toQualified(parameter.getType(), callableDeclaration);

			if(acceptsName(paramType, t))
			{
				continue;
			}

			if(!t.equals(paramType))
			{
				//System.out.println(t + "<>" + paramType);
				return false;
			}
		}
		return true;
	}

	@Nullable
	@Override
	public String getSuperClassName(@Nullable String className)
	{
		String superClass = myBaseClassNames.get(className);
		if(superClass != null)
		{
			return superClass;
		}
		else
		{
			NavigatablePsiElement aClass = findClass(className);
			if(aClass != null)
			{
				//return Object.class.getName();
			}
		}
		return super.getSuperClassName(className);
	}

	@Nonnull
	@Override
	public List<String> getAnnotations(@Nullable NavigatablePsiElement element)
	{
		List<String> result = new ArrayList<>();
		if(element instanceof MethodDelegate)
		{
			CallableDeclaration delegate = ((MethodDelegate) element).getDelegate();

			NodeList<AnnotationExpr> annotations = delegate.getAnnotations();
			for(AnnotationExpr annotation : annotations)
			{
				//result.add(toQualified(annotation.getName().asString()));
			}
		}
		else
		{
			throw new UnsupportedOperationException();
		}
		return result;
	}

	@Nonnull
	@Override
	public String getDeclaringClass(@Nullable NavigatablePsiElement method)
	{
		if(method instanceof MethodDelegate)
		{
			return ((MethodDelegate) method).myDeclarationQName;
		}
		throw new UnsupportedOperationException();
	}

	@Nonnull
	@Override
	public List<String> getMethodTypes(@Nullable NavigatablePsiElement method)
	{
		if(!(method instanceof MethodDelegate))
		{
			return Collections.emptyList();
		}

		CallableDeclaration<?> delegate = ((MethodDelegate) method).getDelegate();

		List<String> list = new ArrayList<>();
		if(delegate instanceof ConstructorDeclaration)
		{
			list.add("");
		}
		else
		{
			list.add(toQualified(((MethodDeclaration) delegate).getType(), delegate));
		}

		for(Parameter parameter : delegate.getParameters())
		{
			list.add(toQualified(parameter.getType(), delegate));

			list.add(parameter.getNameAsString());
		}

		return list;
	}

	private String toQualified(Type type, CallableDeclaration<?> callableDeclaration)
	{

		ResolvedType resolvedType = null;
		try
		{
			resolvedType = type.resolve();
		}
		catch(Exception ignored)
		{
		}

		String typeText = type.asString();

		Optional<Node> parentNode = callableDeclaration.getParentNode();
		if(parentNode.isPresent())
		{
			Node node = parentNode.get();

			if(node instanceof ClassOrInterfaceDeclaration)
			{
				NodeList<TypeParameter> typeParameters = ((ClassOrInterfaceDeclaration) node).getTypeParameters();

				for(TypeParameter typeParameter : typeParameters)
				{
					if(Objects.equals(typeText, typeParameter.getName().asString()))
					{
						return "<" + typeText + ">";
					}
				}
			}
		}

		if(resolvedType instanceof ResolvedReferenceType)
		{
			ResolvedReferenceType referenceType = (ResolvedReferenceType) resolvedType;

			return referenceType.getQualifiedName();
		}

		String typeString = type.asString();

		String r = myRuleClassNames.get(typeString);
		if(r != null)
		{
			return r;
		}

		if(typeString.startsWith("Trinity"))
		{
			ClassOrInterfaceType cls = type.asClassOrInterfaceType();

			Optional<NodeList<Type>> typeArguments = cls.getTypeArguments();

			List<String> types = new ArrayList<>();
			for(Type arg : typeArguments.get())
			{
				types.add(toQualified(arg, callableDeclaration));
			}
			return Trinity.class.getName() + "<" + String.join(",", types) + ">";
		}

		switch(typeString)
		{
			case "String":
				return String.class.getName();
			case "ResolveState":
				return ResolveState.class.getName();
			case "PsiReference":
				return PsiReference.class.getName();
			case "PsiReference[]":
				return PsiReference.class.getName() + "[]";
			case "TextRange":
				return TextRange.class.getName();
			case "PsiElement":
				return PsiElement.class.getName();
			case "PsiDirectory":
				return PsiDirectory.class.getName();
			case "PsiScopeProcessor":
				return PsiScopeProcessor.class.getName();
			case "Nonnull":
				return Nonnull.class.getName();
			case "Nullable":
				return Nullable.class.getName();
			case "IStubElementType":
				return IStubElementType.class.getName();
			case "ReadWriteAccessDetector.Access":
				return ReadWriteAccessDetector.Access.class.getName().replace("$", ".");
			case "SearchScope":
				return SearchScope.class.getName();
			case "ItemPresentation":
				return ItemPresentation.class.getName();
		}
		return typeString;
	}

	public void setRuleClassNames(BiMap<String, String> rules)
	{
		myRuleClassNames = rules;
	}

	public void setBaseClassNames(Map<String, String> baseClassNames)
	{
		myBaseClassNames = baseClassNames;
	}
}
