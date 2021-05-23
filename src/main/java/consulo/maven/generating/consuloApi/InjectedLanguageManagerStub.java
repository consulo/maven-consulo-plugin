package consulo.maven.generating.consuloApi;

import com.intellij.injected.editor.DocumentWindow;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiLanguageInjectionHost;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;

/**
 * @author VISTALL
 * @since 22/05/2021
 */
public class InjectedLanguageManagerStub extends InjectedLanguageManager
{
	@Override
	public PsiLanguageInjectionHost getInjectionHost(@Nonnull FileViewProvider fileViewProvider)
	{
		return null;
	}

	@Nullable
	@Override
	public PsiLanguageInjectionHost getInjectionHost(@Nonnull PsiElement psiElement)
	{
		return null;
	}

	@Nonnull
	@Override
	public TextRange injectedToHost(@Nonnull PsiElement psiElement, @Nonnull TextRange textRange)
	{
		return textRange;
	}

	@Override
	public int injectedToHost(@Nonnull PsiElement psiElement, int i)
	{
		return 0;
	}

	@Override
	public int injectedToHost(@Nonnull PsiElement psiElement, int i, boolean b)
	{
		return 0;
	}

	@Nonnull
	@Override
	public String getUnescapedText(@Nonnull PsiElement psiElement)
	{
		return psiElement.getText();
	}

	@Nonnull
	@Override
	public List<TextRange> intersectWithAllEditableFragments(@Nonnull PsiFile psiFile, @Nonnull TextRange textRange)
	{
		return List.of();
	}

	@Override
	public boolean isInjectedFragment(@Nonnull PsiFile psiFile)
	{
		return false;
	}

	@Nullable
	@Override
	public PsiElement findInjectedElementAt(@Nonnull PsiFile psiFile, int i)
	{
		return null;
	}

	@Nullable
	@Override
	public List<Pair<PsiElement, TextRange>> getInjectedPsiFiles(@Nonnull PsiElement psiElement)
	{
		return null;
	}

	@Override
	public void dropFileCaches(@Nonnull PsiFile psiFile)
	{

	}

	@Override
	public PsiFile getTopLevelFile(@Nonnull PsiElement psiElement)
	{
		return null;
	}

	@Nonnull
	@Override
	public List<DocumentWindow> getCachedInjectedDocumentsInRange(@Nonnull PsiFile psiFile, @Nonnull TextRange textRange)
	{
		return List.of();
	}

	@Override
	public void enumerate(@Nonnull PsiElement psiElement, @Nonnull PsiLanguageInjectionHost.InjectedPsiVisitor injectedPsiVisitor)
	{

	}

	@Override
	public void enumerateEx(@Nonnull PsiElement psiElement, @Nonnull PsiFile psiFile, boolean b, @Nonnull PsiLanguageInjectionHost.InjectedPsiVisitor injectedPsiVisitor)
	{

	}

	@Nonnull
	@Override
	public List<TextRange> getNonEditableFragments(@Nonnull DocumentWindow documentWindow)
	{
		return List.of();
	}

	@Override
	public boolean mightHaveInjectedFragmentAtOffset(@Nonnull Document document, int i)
	{
		return false;
	}

	@Nonnull
	@Override
	public DocumentWindow freezeWindow(@Nonnull DocumentWindow documentWindow)
	{
		return documentWindow;
	}
}
