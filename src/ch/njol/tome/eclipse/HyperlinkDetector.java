package ch.njol.tome.eclipse;

import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jface.text.IRegion;
import org.eclipse.jface.text.ITextViewer;
import org.eclipse.jface.text.hyperlink.IHyperlink;
import org.eclipse.jface.text.hyperlink.IHyperlinkDetector;
import org.eclipse.jface.text.hyperlink.IHyperlinkDetectorExtension2;
import org.eclipse.swt.SWT;

import ch.njol.tome.ast.ASTElement;
import ch.njol.tome.ast.ASTElementPart;
import ch.njol.tome.ast.ASTLink;
import ch.njol.tome.ast.ASTMembers.ASTMemberModifiers;
import ch.njol.tome.ast.ASTTopLevelElements.ASTSourceFile;
import ch.njol.tome.ast.ASTTopLevelElements.ASTModuleIdentifier;
import ch.njol.tome.compiler.ASTModule;
import ch.njol.tome.compiler.SourceCodeLinkable;
import ch.njol.tome.compiler.Token;
import ch.njol.tome.compiler.Token.SymbolToken;
import ch.njol.tome.compiler.Token.WordOrSymbols;
import ch.njol.tome.compiler.Token.WordToken;
import ch.njol.tome.eclipse.Plugin.DocumentData;
import ch.njol.tome.ir.definitions.IRMemberRedefinition;

class HyperlinkDetector implements IHyperlinkDetector, IHyperlinkDetectorExtension2 {
	
	Editor editor;
	
	HyperlinkDetector(final Editor editor) {
		this.editor = editor;
	}
	
	@Override
	public int getStateMask() {
		return SWT.CONTROL;
	}
	
	@Override
	public IHyperlink @Nullable [] detectHyperlinks(@SuppressWarnings("null") final ITextViewer textViewer,
			@SuppressWarnings("null") final IRegion region, final boolean canShowMultipleHyperlinks) {
		final DocumentData<?> data = editor.getData();
		if (data == null)
			return null;
		final Token t = data.tokens.getTokenAt(region.getOffset(), true);
		final ASTElement parent = t == null ? null : t.parent();
		if (t == null || parent == null)
			return null;
		// TODO link to definition of methods (i.e. the interface where it is declared),
		// their primary return type (or all of them), etc.
		// TODO also link keywords (like override) and symbols (mostly operators)
		if (parent instanceof ASTModuleIdentifier && !(parent instanceof ASTModule)) { // i.e. any module identifier
																						// that is not actually the
																						// identifier of the module
																						// itself
			ASTModule ownModule = t.getParentOfType(ASTModule.class);
			if (ownModule == null) {
				final ASTSourceFile bf = t.getParentOfType(ASTSourceFile.class);
				if (bf != null)
					ownModule = bf.module;
			}
			final ASTModule targetModule = ownModule == null ? null
					: ownModule.modules.get(((ASTModuleIdentifier) parent).identifier);
			if (targetModule == null)
				return null;
			return new IHyperlink[] {new Hyperlink(parent, targetModule)};
		} else if (t instanceof WordToken) {
			final String name = ((WordToken) t).word;
			if (((WordToken) t).keyword) {
				switch (name) {
					case "override": { // 'override x as y' only links 'x' without this, while everything else links
										// 'override'
						final ASTMemberModifiers mm = t.getParentOfType(ASTMemberModifiers.class);
						if (mm == null)
							return null;
						final IRMemberRedefinition e = mm.overridden.get();
						if (e == null)
							return null;
						final ASTElementPart elem = e.getLinked();
						return elem == null ? null : new IHyperlink[] {new Hyperlink(t, elem)};
					}
				}
			}
			for (final ASTLink<?> link : parent.links()) {
				if (link.getName() == name) {
					final Object e = link.get();
					if (e == null || !(e instanceof SourceCodeLinkable))
						return null;
					final ASTElementPart elem = ((SourceCodeLinkable) e).getLinked();
					return elem == null ? null : new IHyperlink[] {new Hyperlink(t, elem)};
				}
			}
		} else if (t instanceof SymbolToken) {
			for (final ASTLink<?> link : parent.links()) {
				final WordOrSymbols nameToken = link.getNameToken();
				if (nameToken != null && nameToken.tokens().stream().anyMatch(t2 -> t2 == t)) {
					final Object e = link.get();
					if (e == null || !(e instanceof SourceCodeLinkable))
						return null;
					final ASTElementPart elem = ((SourceCodeLinkable) e).getLinked();
					return elem == null ? null : new IHyperlink[] {new Hyperlink(nameToken, elem)};
				}
			}
		}
		return null;
	}
}