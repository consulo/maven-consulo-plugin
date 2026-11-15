package consulo.maven.generating;

import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.PackageDeclaration;
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
import maven.bnf.consulo.annotation.access.RequiredReadAction;
import maven.bnf.consulo.language.psi.NavigatablePsiElement;
import maven.bnf.org.intellij.grammar.java.JavaHelper;
import org.apache.maven.plugin.logging.Log;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.nio.file.Paths;
import java.util.*;
import java.util.function.Predicate;

/**
 * @author VISTALL
 * @since 2018-06-18
 */
class JavaParserJavaHelper extends JavaHelper {
    static class TypeDeclarationDelegate extends MyElement<TypeDeclaration> {
        protected TypeDeclarationDelegate(TypeDeclaration delegate) {
            super(delegate);
        }
    }

    static class MethodDelegate extends MyElement<CallableDeclaration> {
        private final String myDeclarationQName;

        protected MethodDelegate(CallableDeclaration delegate, String declarationQName) {
            super(delegate);
            myDeclarationQName = declarationQName;
        }

        @RequiredReadAction
        @Override
        public String getName() {
            return getDelegate().getNameAsString();
        }
    }

    private SourceRoot mySourceRoot;

    private SourceRoot myGeneratedRoot;

    private Map<String, TypeDeclaration> myUnits = new HashMap<>();
    private BiMap<String, String> myRuleClassNames;
    private Map<String, String> myBaseClassNames;
    private final Log myLog;

    public JavaParserJavaHelper(String sourceDirectory, String directoryToGenerate, Log log) {
        myLog = log;
        ParserConfiguration configuration = new ParserConfiguration();
        configuration.setLanguageLevel(ParserConfiguration.LanguageLevel.BLEEDING_EDGE);
        configuration.setSymbolResolver(new JavaSymbolSolver(new JavaParserTypeSolver(sourceDirectory)));

        mySourceRoot = new SourceRoot(Paths.get(sourceDirectory), configuration);
        myGeneratedRoot = new SourceRoot(Paths.get(directoryToGenerate), configuration);
    }

    @Nullable
    @Override
    public NavigatablePsiElement findClass(@Nullable String className) {
        NavigatablePsiElement impl = findClassImpl(className, mySourceRoot);
        if (impl != null) {
            return impl;
        }
        impl = findClassImpl(className, myGeneratedRoot);
        if (impl != null) {
            return impl;
        }
        //System.out.println("class not found " + className);
        return impl;
    }

    private NavigatablePsiElement findClassImpl(@Nullable String className, SourceRoot sourceRoot) {
        assert className != null;

        TypeDeclaration typeDeclaration = myUnits.get(className);
        if (typeDeclaration != null) {
            return new TypeDeclarationDelegate(typeDeclaration);
        }

        try {
            CompilationUnit unit = sourceRoot.parse("", className.replace(".", "/") + ".java");
            for (TypeDeclaration<?> declaration : unit.getTypes()) {
                if (declaration.getFullyQualifiedName().get().equals(className)) {
                    typeDeclaration = declaration;
                    break;
                }
            }
        }
        catch (Exception e) {
            if (myLog.isDebugEnabled()) {
                myLog.debug(e);
            }
        }

        if (typeDeclaration == null) {
            return null;
        }

        myUnits.put(className, typeDeclaration);

        return new TypeDeclarationDelegate(typeDeclaration);
    }

    @Nonnull
    @Override
    public List<NavigatablePsiElement> findClassMethods(@Nullable String version, @Nullable String className, @Nonnull MethodType methodType, @Nullable String methodName, int paramCount, String... paramTypes) {
        //System.out.println(className);
        if (className == null) {
            return List.of();
        }

        NavigatablePsiElement element = findClass(className);
        TypeDeclaration declaration = element instanceof TypeDeclarationDelegate ? ((TypeDeclarationDelegate) element).getDelegate() : null;
        if (declaration == null) {
            return List.of();
        }

        List<NavigatablePsiElement> methodDelegates = new ArrayList<>();

        NodeList<BodyDeclaration<?>> members = declaration.getMembers();
        loop:
        for (BodyDeclaration<?> member : members) {
            if (member instanceof MethodDeclaration && methodType != MethodType.CONSTRUCTOR) {
                switch (methodType) {
                    case STATIC:
                        if (!((MethodDeclaration) member).getModifiers().contains(Modifier.staticModifier())) {
                            continue loop;
                        }
                        break;
                    case INSTANCE:
                        if (((MethodDeclaration) member).getModifiers().contains(Modifier.staticModifier())) {
                            continue loop;
                        }
                        break;
                }

                if (!acceptsName(methodName, ((MethodDeclaration) member).getNameAsString())) {
                    continue;
                }

                if (!acceptMethod(version, (MethodDeclaration) member, paramCount, paramTypes)) {
                    continue;
                }

                methodDelegates.add(new MethodDelegate((MethodDeclaration) member, className));
            }
            else if (member instanceof ConstructorDeclaration && methodType == MethodType.CONSTRUCTOR) {
                if (!acceptMethod(version, (ConstructorDeclaration) member, paramCount, paramTypes)) {
                    continue;
                }

                methodDelegates.add(new MethodDelegate((ConstructorDeclaration) member, className));
            }
        }

        return methodDelegates;
    }

    private boolean acceptMethod(String version, CallableDeclaration<?> callableDeclaration, int paramsCount, String[] paramTypes) {
        NodeList<Parameter> parameters = callableDeclaration.getParameters();
        if (paramsCount > 0 && parameters.size() != paramsCount) {
            return false;
        }

        if (parameters.size() == 0) {
            return true;
        }

        if (parameters.size() < paramTypes.length) {
            return false;
        }

        for (int i = 0; i < paramTypes.length; i++) {
            String paramType = paramTypes[i];
            Parameter parameter = parameters.get(i);

            String t = toQualified(version, parameter.getType(), callableDeclaration);

            if (acceptsName(paramType, t)) {
                continue;
            }

            if (!t.equals(paramType) && !isSubtypeOf(paramType, t, new HashSet<>())) {
                //System.out.println(t + "<>" + paramType);
                return false;
            }
        }
        return true;
    }

    @Nullable
    @Override
    public String getSuperClassName(@Nullable String className) {
        if (Objects.equals(className, Object.class.getName())) {
            return null;
        }

        String superClass = myBaseClassNames.get(className);
        if (superClass != null) {
            return superClass;
        }
        else {
            NavigatablePsiElement aClass = findClass(className);
            if (aClass != null) {
                //return Object.class.getName();
            }
        }
        return Object.class.getName();
    }

    @Nonnull
    @Override
    public List<String> getAnnotations(@Nullable NavigatablePsiElement element) {
        List<String> result = new ArrayList<>();
        if (element instanceof MethodDelegate) {
            CallableDeclaration delegate = ((MethodDelegate) element).getDelegate();

            NodeList<AnnotationExpr> annotations = delegate.getAnnotations();
            for (AnnotationExpr annotation : annotations) {
                //result.add(toQualified(annotation.getName().asString()));
            }
        }
        else {
            throw new UnsupportedOperationException();
        }
        return result;
    }

    @Nonnull
    @Override
    public String getDeclaringClass(@Nullable NavigatablePsiElement method) {
        if (method instanceof MethodDelegate) {
            return ((MethodDelegate) method).myDeclarationQName;
        }
        throw new UnsupportedOperationException();
    }

    @Nonnull
    @Override
    public List<String> getMethodTypes(@Nullable String version, @Nullable NavigatablePsiElement method) {
        if (!(method instanceof MethodDelegate)) {
            return List.of();
        }

        CallableDeclaration<?> delegate = ((MethodDelegate) method).getDelegate();

        List<String> list = new ArrayList<>();
        if (delegate instanceof ConstructorDeclaration) {
            list.add("");
        }
        else {
            list.add(toQualified(version, ((MethodDeclaration) delegate).getType(), delegate));
        }

        for (Parameter parameter : delegate.getParameters()) {
            list.add(toQualified(version, parameter.getType(), delegate));

            list.add(parameter.getNameAsString());
        }

        return list;
    }

    private String toQualified(String version, Type type, CallableDeclaration<?> callableDeclaration) {
        ResolvedType resolvedType = null;
        try {
            resolvedType = type.resolve();
        }
        catch (Exception ignored) {
        }

        String typeText = type.asString();

        Optional<Node> parentNode = callableDeclaration.getParentNode();
        if (parentNode.isPresent()) {
            Node node = parentNode.get();

            if (node instanceof ClassOrInterfaceDeclaration) {
                NodeList<TypeParameter> typeParameters = ((ClassOrInterfaceDeclaration) node).getTypeParameters();

                for (TypeParameter typeParameter : typeParameters) {
                    if (Objects.equals(typeText, typeParameter.getName().asString())) {
                        return "<" + typeText + ">";
                    }
                }
            }
        }

        if (resolvedType instanceof ResolvedReferenceType) {
            ResolvedReferenceType referenceType = (ResolvedReferenceType) resolvedType;

            return referenceType.getQualifiedName();
        }

        String typeString = type.asString();

        String r = myRuleClassNames.get(typeString);
        if (r != null) {
            return r;
        }

        String qualifiedByImports = qualifyByImports(type, callableDeclaration);
        if (qualifiedByImports != null) {
            return qualifiedByImports;
        }

        if (typeString.startsWith("Trinity")) {
            ClassOrInterfaceType cls = type.asClassOrInterfaceType();

            Optional<NodeList<Type>> typeArguments = cls.getTypeArguments();

            List<String> types = new ArrayList<>();
            for (Type arg : typeArguments.get()) {
                types.add(toQualified(version, arg, callableDeclaration));
            }
            return "consulo.util.lang.Trinity<" + String.join(",", types) + ">";
        }

        switch (typeString) {
            case "Predicate":
                return Predicate.class.getName();
            case "String":
                return String.class.getName();
            case "ResolveState":
                return "consulo.language.psi.resolve.ResolveState";
            case "PsiReference":
                return "consulo.language.psi.PsiReference";
            case "PsiReference[]":
                return "consulo.language.psi.PsiReference[]";
            case "TextRange":
                return "consulo.document.util.TextRange";
            case "PsiElement":
                return "consulo.language.psi.PsiElement";
            case "PsiDirectory":
                return "consulo.language.psi.PsiDirectory";
            case "PsiScopeProcessor":
                return "consulo.language.psi.resolve.PsiScopeProcessor";
            case "Nonnull":
                return jakarta.annotation.Nonnull.class.getName();
            case "Nullable":
                return jakarta.annotation.Nullable.class.getName();
            case "IStubElementType":
                return "consulo.language.psi.stub.IStubElementType";
            case "IElementType":
                return "consulo.language.ast.IElementType";
            case "ReadWriteAccessDetector.Access":
                return "consulo.language.editor.highlight.ReadWriteAccessDetector.Access";
            case "SearchScope":
                return "consulo.content.scope.SearchScope";
            case "ItemPresentation":
                return "consulo.navigation.ItemPresentation";
        }
        return typeString;
    }

    /**
     * Checks whether {@code className} (a class from the source or generated roots) extends or implements {@code superQName}.
     * Lets psiImplUtil methods take a super interface of the rule class, as grammar-kit of IntelliJ does.
     */
    private boolean isSubtypeOf(String className, String superQName, Set<String> visited) {
        if (className == null || !visited.add(className)) {
            return false;
        }
        NavigatablePsiElement element = findClass(className);
        if (!(element instanceof TypeDeclarationDelegate)) {
            return false;
        }
        TypeDeclaration<?> declaration = ((TypeDeclarationDelegate) element).getDelegate();
        if (!(declaration instanceof ClassOrInterfaceDeclaration)) {
            return false;
        }
        ClassOrInterfaceDeclaration classDeclaration = (ClassOrInterfaceDeclaration) declaration;
        List<ClassOrInterfaceType> superTypes = new ArrayList<>(classDeclaration.getExtendedTypes());
        superTypes.addAll(classDeclaration.getImplementedTypes());
        for (ClassOrInterfaceType superType : superTypes) {
            String superName = qualifyName(superType.getNameWithScope(), classDeclaration);
            if (superName == null) {
                continue;
            }
            if (superName.equals(superQName) || isSubtypeOf(superName, superQName, visited)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Qualifies a type which the symbol solver can not resolve (types from dependencies are not on its path)
     * by the imports and the package of the compilation unit which declares it. Type arguments and arrays are kept.
     */
    @Nullable
    private String qualifyByImports(Type type, Node context) {
        if (type.isArrayType()) {
            String component = qualifyByImports(type.asArrayType().getComponentType(), context);
            return component == null ? null : component + "[]";
        }
        if (!type.isClassOrInterfaceType()) {
            return null;
        }
        ClassOrInterfaceType classType = type.asClassOrInterfaceType();
        String name = qualifyName(classType.getNameWithScope(), context);
        if (name == null) {
            return null;
        }
        Optional<NodeList<Type>> typeArguments = classType.getTypeArguments();
        if (typeArguments.isEmpty() || typeArguments.get().isEmpty()) {
            return name;
        }
        List<String> args = new ArrayList<>();
        for (Type arg : typeArguments.get()) {
            String qualifiedArg = qualifyByImports(arg, context);
            args.add(qualifiedArg != null ? qualifiedArg : arg.asString());
        }
        return name + "<" + String.join(",", args) + ">";
    }

    @Nullable
    private String qualifyName(String name, Node context) {
        Optional<CompilationUnit> unit = context.findCompilationUnit();
        if (unit.isEmpty()) {
            return null;
        }
        int dot = name.indexOf('.');
        String first = dot < 0 ? name : name.substring(0, dot);
        String rest = dot < 0 ? "" : name.substring(dot);
        for (ImportDeclaration importDeclaration : unit.get().getImports()) {
            if (importDeclaration.isStatic() || importDeclaration.isAsterisk()) {
                continue;
            }
            String imported = importDeclaration.getNameAsString();
            if (imported.equals(first) || imported.endsWith("." + first)) {
                return imported + rest;
            }
        }
        for (ImportDeclaration importDeclaration : unit.get().getImports()) {
            if (!importDeclaration.isStatic() && importDeclaration.isAsterisk()) {
                String candidate = importDeclaration.getNameAsString() + "." + first;
                if (findClass(candidate) != null) {
                    return candidate + rest;
                }
            }
        }
        if (Character.isLowerCase(first.charAt(0)) && dot > 0) {
            // already qualified
            return name;
        }
        String packageName = unit.get().getPackageDeclaration().map(PackageDeclaration::getNameAsString).orElse("");
        String samePackage = packageName.isEmpty() ? name : packageName + "." + name;
        if (findClass(samePackage) != null) {
            return samePackage;
        }
        if (dot < 0 && JAVA_LANG.contains(name)) {
            return "java.lang." + name;
        }
        return null;
    }

    private static final Set<String> JAVA_LANG = Set.of("Object", "String", "Integer", "Long", "Boolean", "Character", "Byte", "Short",
        "Double", "Float", "Number", "Void", "Class", "Iterable", "CharSequence", "Enum", "Comparable", "Runnable", "Throwable");

    public void setRuleClassNames(BiMap<String, String> rules) {
        myRuleClassNames = rules;
    }

    public void setBaseClassNames(Map<String, String> baseClassNames) {
        myBaseClassNames = baseClassNames;
    }
}
