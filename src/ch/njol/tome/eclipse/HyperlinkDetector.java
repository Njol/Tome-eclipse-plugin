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
import ch.njol.tome.compiler.SourceCodeLinkable;
import ch.njol.tome.compiler.Token;
import ch.njol.tome.eclipse.Plugin.DocumentData;

class HyperlinkDetector implements IHyperlinkDetector, IHyperlinkDetectorExtension2 {
	
	Editor editor;
	
	HyperlinkDetector(final Editor editor) {
		this.editor = editor;
	}
	
	@Override
	public int getStateMask() {
		return SWT.CONTROL;
	}
	
	// TODO link to definition of methods (i.e. the interface where it is declared),
	// their primary return type (or all of them), etc.
	// TODO also link keywords (like override) and symbols (mostly operators)
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
		
		final SourceCodeLinkable linkable = parent.getLinked(t);
		if (linkable == null)
			return null;
		final ASTElementPart linked = linkable.getLinked();
		if (linked == null)
			return null;
		return new IHyperlink[] {new Hyperlink(t, linked)};
	}
}
