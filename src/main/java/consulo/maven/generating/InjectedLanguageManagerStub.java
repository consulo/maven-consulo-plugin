package consulo.maven.generating;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import maven.bnf.consulo.annotation.access.RequiredReadAction;
import maven.bnf.consulo.document.Document;
import maven.bnf.consulo.document.DocumentWindow;
import maven.bnf.consulo.document.util.TextRange;
import maven.bnf.consulo.language.file.FileViewProvider;
import maven.bnf.consulo.language.inject.InjectedLanguageManager;
import maven.bnf.consulo.language.inject.MultiHostRegistrar;
import maven.bnf.consulo.language.psi.PsiElement;
import maven.bnf.consulo.language.psi.PsiFile;
import maven.bnf.consulo.language.psi.PsiLanguageInjectionHost;
import maven.bnf.consulo.util.lang.Pair;

import java.util.List;
import java.util.function.Function;

/**
 * @author VISTALL
 * @since 2023-11-10
 */
public class InjectedLanguageManagerStub extends InjectedLanguageManager {
    @Override
    public PsiLanguageInjectionHost getInjectionHost(@Nonnull FileViewProvider injectedProvider) {
        return null;
    }

    @Nullable
    @Override
    public PsiLanguageInjectionHost getInjectionHost(@Nonnull PsiElement injectedElement) {
        return null;
    }

    @Nonnull
    @Override
    public TextRange injectedToHost(@Nonnull PsiElement injectedContext, @Nonnull TextRange injectedTextRange) {
        return null;
    }

    @Override
    public int injectedToHost(@Nonnull PsiElement injectedContext, int injectedOffset) {
        return 0;
    }

    @Override
    public int injectedToHost(@Nonnull PsiElement injectedContext, int injectedOffset, boolean minHostOffset) {
        return 0;
    }

    @Nonnull
    @Override
    public String getUnescapedText(@Nonnull PsiElement injectedNode) {
        return null;
    }

    @Nonnull
    @Override
    public List<TextRange> intersectWithAllEditableFragments(@Nonnull PsiFile injectedPsi, @Nonnull TextRange rangeToEdit) {
        return null;
    }

    @Override
    public boolean isInjectedFragment(@Nonnull PsiFile injectedFile) {
        return false;
    }

    @Nullable
    @Override
    public PsiElement findInjectedElementAt(@Nonnull PsiFile hostFile, int hostDocumentOffset) {
        return null;
    }

    @Nullable
    @Override
    public PsiElement findElementAtNoCommit(@Nonnull PsiFile file, int offset) {
        return null;
    }

    @Nullable
    @Override
    public List<Pair<PsiElement, TextRange>> getInjectedPsiFiles(@Nonnull PsiElement host) {
        return null;
    }

    @Override
    public void dropFileCaches(@Nonnull PsiFile file) {

    }

    @Override
    public PsiFile getTopLevelFile(@Nonnull PsiElement element) {
        return null;
    }

    @Nonnull
    @Override
    public List<DocumentWindow> getCachedInjectedDocumentsInRange(@Nonnull PsiFile hostPsiFile, @Nonnull TextRange range) {
        return List.of();
    }

    @Override
    public void enumerate(@Nonnull PsiElement host, @Nonnull PsiLanguageInjectionHost.InjectedPsiVisitor visitor) {

    }

    @RequiredReadAction
    @Override
    public void enumerateEx(@Nonnull PsiElement host, @Nonnull PsiFile containingFile, boolean probeUp, @Nonnull PsiLanguageInjectionHost.InjectedPsiVisitor visitor) {

    }

    @Nonnull
    @Override
    public List<TextRange> getNonEditableFragments(@Nonnull DocumentWindow window) {
        return List.of();
    }

    @Override
    public boolean mightHaveInjectedFragmentAtOffset(@Nonnull Document hostDocument, int hostOffset) {
        return false;
    }

    @Nonnull
    @Override
    public DocumentWindow freezeWindow(@Nonnull DocumentWindow document) {
        return document;
    }

    @Nullable
    @Override
    public PsiLanguageInjectionHost.Place getShreds(@Nonnull PsiFile injectedFile) {
        return null;
    }

    @Nullable
    @Override
    public PsiLanguageInjectionHost.Place getShreds(@Nonnull FileViewProvider viewProvider) {
        return null;
    }

    @Nonnull
    @Override
    public PsiLanguageInjectionHost.Place getShreds(@Nonnull DocumentWindow documentWindow) {
        return null;
    }

    @Override
    protected void injectLanguagesFromConcatenationAdapter(@Nonnull MultiHostRegistrar registrar,
                                                           @Nonnull PsiElement context,
                                                           @Nonnull Function<PsiElement, Pair<PsiElement, PsiElement[]>> computeAnchorAndOperandsFunc) {

    }
}
