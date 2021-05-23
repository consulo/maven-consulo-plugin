package consulo.maven.generating.consuloApi;

import com.intellij.openapi.project.Project;
import com.intellij.psi.impl.DocumentCommitProcessor;
import com.intellij.psi.impl.PsiDocumentManagerBase;

import javax.annotation.Nonnull;

/**
 * @author VISTALL
 * @since 22/05/2021
 */
public class PsiDocumentManagerStub extends PsiDocumentManagerBase
{
	public PsiDocumentManagerStub(@Nonnull Project project, DocumentCommitProcessor documentCommitProcessor)
	{
		super(project, documentCommitProcessor);
	}
}
