package ch.njol.tome.eclipse;

import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jface.text.ITextViewer;
import org.eclipse.jface.text.contentassist.CompletionProposal;
import org.eclipse.jface.text.contentassist.ICompletionProposal;
import org.eclipse.jface.text.contentassist.IContentAssistProcessor;
import org.eclipse.jface.text.contentassist.IContextInformation;
import org.eclipse.jface.text.contentassist.IContextInformationValidator;

import ch.njol.tome.ast.ASTElement;
import ch.njol.tome.common.ContentAssistProposal;
import ch.njol.tome.compiler.Token;
import ch.njol.tome.compiler.Token.WordToken;
import ch.njol.tome.eclipse.Plugin.DocumentData;

public class ContentAssistProcessor implements IContentAssistProcessor {
	
	private final Editor editor;
	
	public ContentAssistProcessor(final Editor editor) {
		this.editor = editor;
	}
	
	private @Nullable String errorMessage = null;
	
	@Override
	public @Nullable String getErrorMessage() {
		return errorMessage;
	}
	
	// completion proposals
	
	@Override
	public char @NonNull [] getCompletionProposalAutoActivationCharacters() {
		return new char[] {'.', '~', ','};
	}
	
	@Override
	public ICompletionProposal @Nullable [] computeCompletionProposals(final ITextViewer viewer, final int offset) {
		errorMessage = null;
		final DocumentData<?> data = editor.getData();
		if (data == null)
			return null;
		final Token token = data.tokens.getTokenAt(offset, false);
		if (token == null)
			return null;
		final String prefix = token instanceof WordToken ? token.toString().substring(0, offset - token.absoluteRegionStart()) : "";
		final ASTElement astElement = token.parent();
		if (astElement == null)
			return null;
		final Stream<ContentAssistProposal> proposals = astElement.getContentAssistProposals(token, s -> s.startsWith(prefix)); // TODO better matching
		if (proposals == null)
			return null;
		@SuppressWarnings("null")
		final ICompletionProposal[] result = proposals
				.map(p -> new CompletionProposal(p.getReplacementString(), offset - prefix.length(), prefix.length(), p.getReplacementString().length()))
				.collect(Collectors.toList()).toArray(new ICompletionProposal[0]);
		return result;
	}
	
	// context information
	
	@Override
	public char @Nullable [] getContextInformationAutoActivationCharacters() {
		return null;
	}
	
	@Override
	public IContextInformation @Nullable [] computeContextInformation(final ITextViewer viewer, final int offset) {
//		errorMessage = null;
		return null;
	}
	
	@Override
	public @Nullable IContextInformationValidator getContextInformationValidator() {
		return null;
	}
	
}
