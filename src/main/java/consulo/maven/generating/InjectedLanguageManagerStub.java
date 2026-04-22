package consulo.maven.generating;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import maven.bnf.consulo.annotation.access.RequiredReadAction;
import maven.bnf.consulo.document.Document;
import maven.bnf.consulo.document.DocumentWindow;
import maven.bnf.consulo.document.util.TextRange;
import maven.bnf.consulo.language.file.FileViewProvider;
import maven.bnf.consulo.language.inject.InjectedLanguageManager;
import maven.bnf.consulo.language.psi.PsiElement;
import maven.bnf.consulo.language.psi.PsiFile;
import maven.bnf.consulo.language.psi.PsiLanguageInjectionHost;
import maven.bnf.consulo.util.lang.Pair;

import java.util.List;

/**
 * @author VISTALL
 * @since 2023-11-10
 */
public class InjectedLanguageManagerStub implements InjectedLanguageManager {

    @Override
    public PsiLanguageInjectionHost getInjectionHost(@Nonnull FileViewProvider fileViewProvider) {
        return null;
    }

    @Nullable
    @Override
    public PsiLanguageInjectionHost getInjectionHost(@Nonnull PsiElement psiElement) {
        return null;
    }

    @Nonnull
    @Override
    public TextRange injectedToHost(@Nonnull PsiElement psiElement, @Nonnull TextRange textRange) {
        return null;
    }

    @Override
    public int injectedToHost(@Nonnull PsiElement psiElement, int i) {
        return 0;
    }

    @Override
    public int injectedToHost(@Nonnull PsiElement psiElement, int i, boolean b) {
        return 0;
    }

    @Nullable
    @Override
    public String getUnescapedLeafText(PsiElement psiElement, boolean b) {
        return null;
    }

    @Nonnull
    @Override
    public String getUnescapedText(@Nonnull PsiElement psiElement) {
        return null;
    }

    @Nonnull
    @Override
    public List<TextRange> intersectWithAllEditableFragments(@Nonnull PsiFile psiFile, @Nonnull TextRange textRange) {
        return null;
    }

    @Override
    public boolean isInjectedFragment(@Nonnull PsiFile psiFile) {
        return false;
    }

    @Override
    public PsiFile findInjectedPsiNoCommit(@Nonnull PsiFile psiFile, int i) {
        return null;
    }

    @Nullable
    @Override
    public PsiElement findInjectedElementAt(@Nonnull PsiFile psiFile, int i) {
        return null;
    }

    @Nullable
    @Override
    public PsiElement findElementAtNoCommit(@Nonnull PsiFile psiFile, int i) {
        return null;
    }

    @Nullable
    @Override
    public List<Pair<PsiElement, TextRange>> getInjectedPsiFiles(@Nonnull PsiElement psiElement) {
        return null;
    }

    @Override
    public void dropFileCaches(@Nonnull PsiFile psiFile) {

    }

    @Override
    public PsiFile getTopLevelFile(@Nonnull PsiElement psiElement) {
        return null;
    }

    @Nonnull
    @Override
    public List<DocumentWindow> getCachedInjectedDocumentsInRange(@Nonnull PsiFile psiFile, @Nonnull TextRange textRange) {
        return null;
    }

    @Override
    public void enumerate(@Nonnull PsiElement psiElement, @Nonnull PsiLanguageInjectionHost.InjectedPsiVisitor injectedPsiVisitor) {

    }

    @Override
    public void enumerate(@Nonnull DocumentWindow documentWindow, @Nonnull PsiFile psiFile, @Nonnull PsiLanguageInjectionHost.InjectedPsiVisitor injectedPsiVisitor) {

    }

    @Override
    public void enumerateEx(@Nonnull PsiElement psiElement, @Nonnull PsiFile psiFile, boolean b, @RequiredReadAction @Nonnull PsiLanguageInjectionHost.InjectedPsiVisitor injectedPsiVisitor) {

    }

    @Nonnull
    @Override
    public List<TextRange> getNonEditableFragments(@Nonnull DocumentWindow documentWindow) {
        return null;
    }

    @Override
    public boolean mightHaveInjectedFragmentAtOffset(@Nonnull Document document, int i) {
        return false;
    }

    @Nonnull
    @Override
    public DocumentWindow freezeWindow(@Nonnull DocumentWindow documentWindow) {
        return null;
    }

    @Nullable
    @Override
    public PsiLanguageInjectionHost.Place getShreds(@Nonnull PsiFile psiFile) {
        return null;
    }

    @Nullable
    @Override
    public PsiLanguageInjectionHost.Place getShreds(@Nonnull FileViewProvider fileViewProvider) {
        return null;
    }

    @Nonnull
    @Override
    public PsiLanguageInjectionHost.Place getShreds(@Nonnull DocumentWindow documentWindow) {
        return null;
    }
}
