package ch.njol.tome.eclipse;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jface.text.DefaultInformationControl;
import org.eclipse.jface.text.IInformationControl;
import org.eclipse.jface.text.IInformationControlCreator;
import org.eclipse.jface.text.IRegion;
import org.eclipse.jface.text.ITextHover;
import org.eclipse.jface.text.ITextHoverExtension;
import org.eclipse.jface.text.ITextViewer;
import org.eclipse.jface.text.Region;
import org.eclipse.swt.widgets.Shell;

import ch.njol.tome.ast.ASTElement;
import ch.njol.tome.ast.ASTInterfaces.ASTElementWithIR;
import ch.njol.tome.ast.ASTInterfaces.TypedASTElement;
import ch.njol.tome.common.DebugString;
import ch.njol.tome.compiler.Token;
import ch.njol.tome.compiler.Token.CommentToken;
import ch.njol.tome.compiler.Token.WhitespaceToken;
import ch.njol.tome.eclipse.Plugin.DocumentData;
import ch.njol.tome.ir.IRElement;

class TextHover implements ITextHover, ITextHoverExtension {
	
	final Editor editor;
	
	TextHover(final Editor editor) {
		this.editor = editor;
	}
	
	private static class InformationControlCreator implements IInformationControlCreator {
		@Override
		public IInformationControl createInformationControl(final Shell parent) {
			return new DefaultInformationControl(parent); // TODO make styled text and such (e.g. using
															// BrowserInformationControl?) (include images/icons
															// maybe?) (may require ITextHoverExtension2)
		}
	}
	
	@Override
	public @NonNull IInformationControlCreator getHoverControlCreator() {
		return new InformationControlCreator();
	}
	
	@Override
	public @Nullable String getHoverInfo(final ITextViewer textViewer, final IRegion hoverRegion) {
		final int offset = hoverRegion instanceof TokenRegion ? ((TokenRegion) hoverRegion).originalOffset
				: hoverRegion.getOffset();
		final IFile file = (IFile) editor.getEditorInput().getAdapter(IResource.class);
		if (file == null)
			return null;
		String result = "";
		try {
			for (final IMarker m : file.findMarkers(IMarker.MARKER, true, IResource.DEPTH_INFINITE)) {
				if (m.getAttribute(IMarker.CHAR_START, Integer.MAX_VALUE) <= offset
						&& offset <= m.getAttribute(IMarker.CHAR_END, -1)) {
					result += "\n" + m.getAttribute(IMarker.MESSAGE, "");
				}
			}
		} catch (final CoreException ex) {}
		if (hoverRegion instanceof TokenRegion) {
			final Token token = ((TokenRegion) hoverRegion).token;
			final ASTElement e = token.parent();
			if (e instanceof TypedASTElement) {
				result += "\nType: " + ((TypedASTElement) e).getIRType();
			}
		}
		// result += "\nFatal parse errors: ";
		// final ASTElement ast = editor.data.ast;
		// for (final ParseError e : ast.fatalParseErrors()) {
		// if (e.start <= offset && offset <= e.start + e.length) {
		// result += " / " + e.message;
		//// break;
		// }
		// }
		if (hoverRegion instanceof TokenRegion) {
			final Token token = ((TokenRegion) hoverRegion).token;
			final ASTElement e = token.parent();
			final String hoverInfo = e == null ? null : e.hoverInfo(token);
			if (hoverInfo != null)
				result += "\n" + hoverInfo;
			if (!(token instanceof CommentToken || token instanceof WhitespaceToken)) {
				result += "\nElement Hierarchy at token \"" + token + "\":";
				ASTElement p = e;
				while (p != null) {
					result += "\n    > " + p.getClass().getSimpleName();
					if (p instanceof DebugString)
						result += " [" + ((DebugString) p).debug() + "]";
					p = p.parent();
				}
				if (e instanceof ASTElementWithIR) {
					final IRElement ir = ((ASTElementWithIR) e).getIR();
					result += "\nIR: " + ir + " [" + ir.getClass().getSimpleName() + "]"; // TODO more descriptive result
				}
			}
		}
		return result.isEmpty() ? null : result.trim();
	}
	
	@Override
	public IRegion getHoverRegion(@Nullable final ITextViewer textViewer, final int offset) {
		final DocumentData<?> data = editor.getData();
		if (data == null)
			return new Region(offset, 0);
		final Token t = data.tokens.getTokenAt(offset, true);
		return t == null ? new Region(offset, 0) : new TokenRegion(t, offset);
	}
	
}
