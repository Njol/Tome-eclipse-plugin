package ch.njol.tome.eclipse;

import java.util.regex.Matcher;

import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jface.text.DocumentEvent;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IRegion;
import org.eclipse.jface.text.ITypedRegion;
import org.eclipse.jface.text.TextPresentation;
import org.eclipse.jface.text.presentation.IPresentationDamager;
import org.eclipse.jface.text.presentation.IPresentationRepairer;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.StyleRange;
import org.eclipse.swt.graphics.Color;

import ch.njol.tome.ast.ASTElement;
import ch.njol.tome.ast.ASTInterfaces.ASTTypeDeclaration;
import ch.njol.tome.ast.ASTInterfaces.ASTVariable;
import ch.njol.tome.ast.ASTLink;
import ch.njol.tome.ast.expressions.ASTArgument;
import ch.njol.tome.ast.expressions.ASTSimpleTypeUse;
import ch.njol.tome.ast.expressions.ASTUnqualifiedMetaAccess;
import ch.njol.tome.ast.expressions.ASTVariableOrUnqualifiedAttributeUse;
import ch.njol.tome.ast.expressions.ASTVariableOrUnqualifiedAttributeUse.ASTVariableOrUnqualifiedAttributeUseLink;
import ch.njol.tome.ast.members.ASTAttributeDeclaration;
import ch.njol.tome.ast.statements.ASTLambdaMethodCall.ASTLambdaMethodCallPart;
import ch.njol.tome.ast.toplevel.ASTGenericParameterDeclaration;
import ch.njol.tome.compiler.Token;
import ch.njol.tome.compiler.Token.CodeGenerationToken;
import ch.njol.tome.compiler.Token.CommentToken;
import ch.njol.tome.compiler.Token.StringToken;
import ch.njol.tome.compiler.Token.SymbolToken;
import ch.njol.tome.compiler.Token.WordToken;
import ch.njol.tome.eclipse.Plugin.DocumentData;
import ch.njol.tome.ir.definitions.IRAttributeRedefinition;
import ch.njol.tome.ir.definitions.IRVariableOrAttributeRedefinition;
import ch.njol.tome.ir.definitions.IRVariableRedefinition;
import ch.njol.tome.util.TokenListStream;

final class PresentationDamagerRepairer implements IPresentationDamager, IPresentationRepairer {
	
	private final Editor editor;
	private final ColorManager colorManager;
	
	PresentationDamagerRepairer(final Editor editor, final ColorManager colorManager) {
		this.editor = editor;
		this.colorManager = colorManager;
	}
	
	@Override
	public void createPresentation(final TextPresentation presentation, final ITypedRegion region) {
		final DocumentData<?> data = editor.getData();
		if (data == null)
			return;
		final TokenListStream tokens = data.tokens.stream();
		tokens.setTextOffset(region.getOffset());
		do {
			final int tokenStart = tokens.getTextOffset();
			final Token t = tokens.getAndMoveForward();
			if (t == null)
				break;
			ASTElement element = t.parent();
			if (element instanceof ASTLink)
				element = element.parent();
			int fontStyle = SWT.NORMAL;
			Color c = null;
			if ((t instanceof WordToken) && ((WordToken) t).keyword) {
				c = colorManager.keyword();
			} else if (t instanceof SymbolToken) {
				c = colorManager.symbol();
				fontStyle = SWT.BOLD;
			} else if (t instanceof StringToken) {
				c = colorManager.string();
			} else if (t instanceof CodeGenerationToken) {
				c = colorManager.codeGeneration();
			} else if (element instanceof ASTArgument && isLink(t, ((ASTArgument) element).parameterLink)) {
				c = colorManager.parameter();
			} else if (/*
						 * parent instanceof LambdaMethodCall && isToken(t, ((LambdaMethodCall)
						 * parent).method.getName()) // TODO does not work as not direct parent ||
						 */ element instanceof ASTLambdaMethodCallPart
					&& isLink(t, ((ASTLambdaMethodCallPart) element).parameter)) {
				// c = colorManager.lambdaMethodCall();
				c = colorManager.parameter();
			} else if (element instanceof ASTVariableOrUnqualifiedAttributeUse
					&& isLink(t, ((ASTVariableOrUnqualifiedAttributeUse) element).varOrAttributeLink)) {
				final ASTVariableOrUnqualifiedAttributeUseLink varOrAttributeLink = ((ASTVariableOrUnqualifiedAttributeUse) element).varOrAttributeLink;
				final IRVariableOrAttributeRedefinition var = varOrAttributeLink != null ? varOrAttributeLink.get() : null;
				if (var instanceof IRVariableRedefinition) {
					c = colorManager.localVariable();
				} else if (var instanceof IRAttributeRedefinition) {
					c = colorManager.unqualifiedAttribute();
				}
			} else if (element instanceof ASTUnqualifiedMetaAccess && isLink(t, ((ASTUnqualifiedMetaAccess) element).attribute)) {
				c = colorManager.unqualifiedAttribute();
			} else if (element instanceof ASTVariable && isToken(t, ((ASTVariable) element).name())) {
				c = colorManager.localVariable();
			} else if (element instanceof ASTAttributeDeclaration && isToken(t, ((ASTAttributeDeclaration) element).name())) {
				c = colorManager.unqualifiedAttribute();
			} else if (element instanceof ASTSimpleTypeUse
					|| element instanceof ASTTypeDeclaration && t == ((ASTTypeDeclaration) element).nameToken()
					|| element instanceof ASTGenericParameterDeclaration && t == ((ASTGenericParameterDeclaration) element).nameToken()) {
				c = colorManager.type();
			}
			if (c != null || fontStyle != SWT.NORMAL) {
				final StyleRange sr = new StyleRange(tokenStart, t.regionLength(), c, null);
				sr.fontStyle = fontStyle;
				addStyleRange(presentation, sr);
			}
			if (t instanceof CommentToken) {
				final Matcher m = Builder.commentTaskKeywords.matcher(((CommentToken) t).comment);
				int start = 0;
				while (true) {
					if (m.find()) {
						StyleRange sr = new StyleRange(tokenStart + start, m.start() - start, colorManager.comment(), null);
						addStyleRange(presentation, sr);
						sr = new StyleRange(tokenStart + m.start(), m.end() - m.start(), colorManager.commentTask(), null);
						sr.fontStyle = SWT.BOLD;
						addStyleRange(presentation, sr);
						start = m.end();
					} else {
						final StyleRange sr = new StyleRange(tokenStart + start, t.regionLength() - start, colorManager.comment(), null);
						addStyleRange(presentation, sr);
						break;
					}
				}
			}
		} while (tokens.getTextOffset() < region.getOffset() + region.getLength());
		lastRange = null;
	}
	
	private @Nullable StyleRange lastRange = null;
	
	// used for debugging
	@SuppressWarnings("null")
	private void addStyleRange(final TextPresentation presentation, final StyleRange sr) {
		assert lastRange == null || sr.start >= lastRange.start + lastRange.length;
		presentation.addStyleRange(sr);
		lastRange = sr;
	}
	
	final static boolean isToken(final Token t, final @Nullable String value) {
		return t instanceof WordToken && ((WordToken) t).word == value;
	}
	
	private static boolean isLink(final Token t, @Nullable final ASTLink<?> link) {
		return link == null ? false : link.getNameToken() == t;
	}
	
	@Override
	public IRegion getDamageRegion(final ITypedRegion partition, final DocumentEvent event,
			final boolean documentPartitioningChanged) {
		// System.out.println("omfg " + event.getOffset() + "/" + event.getLength());
		if (documentPartitioningChanged)
			return partition;
		
		// data.tokens.setTextOffset(Math.max(0, event.getOffset() - 1));
		// final int start = data.tokens.getTextOffset(); // start of token at start
		// data.tokens.setTextOffset(Math.min(Math.max(event.getLength(),
		// event.getText().length()) + 2, data.tokens.getTextLength()));
		// data.tokens.forward();
		// int end = data.tokens.getTextOffset(); // start of next token at end or end
		// of file = end of token at end
		//
		// Region region = new Region(start, end);
		
		// TODO why doesn't this work reliably? (doesn't even work for the same token
		// that changed...)
		// return region;
		return partition;
	}
	
	@Override
	public void setDocument(final IDocument doc) {}
}
