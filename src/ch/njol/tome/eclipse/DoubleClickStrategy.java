package ch.njol.tome.eclipse;

import org.eclipse.jface.text.DefaultTextDoubleClickStrategy;
import org.eclipse.jface.text.ITextDoubleClickStrategy;
import org.eclipse.jface.text.ITextViewer;

import ch.njol.tome.ast.ASTElement;
import ch.njol.tome.ast.expressions.ASTOperatorExpression;
import ch.njol.tome.compiler.Token;
import ch.njol.tome.compiler.Token.CommentToken;
import ch.njol.tome.compiler.Token.StringToken;
import ch.njol.tome.compiler.Token.SymbolToken;
import ch.njol.tome.compiler.TokenList;
import ch.njol.tome.eclipse.Plugin.DocumentData;
import ch.njol.tome.util.TokenListStream;

final class DoubleClickStrategy implements ITextDoubleClickStrategy {
	
	private final static String brackets = "(){}[]<>";
	
	private final Editor editor;
	
	public DoubleClickStrategy(final Editor editor) {
		this.editor = editor;
	}
	
	@Override
	public void doubleClicked(final ITextViewer viewer) {
		final int offset = viewer.getSelectedRange().x;
		final DocumentData<?> data = editor.getData();
		if (data == null)
			return;
		final TokenList tokens = data.tokens;
		final Token left = tokens.getTokenAt(offset, false), right = tokens.getTokenAt(offset, true);
		
		final char leftSymbol = left instanceof SymbolToken ? ((SymbolToken) left).symbol : '\0', //
				rightSymbol = right instanceof SymbolToken ? ((SymbolToken) right).symbol : '\0';
		
		int bracket1 = offset;
		boolean usingLeftBracket = true;
		int bracketIndex = brackets.indexOf(leftSymbol);
		if (bracketIndex < 0) {
			bracketIndex = brackets.indexOf(rightSymbol);
			usingLeftBracket = false;
		} else {
			bracket1--;
		}
		if (bracketIndex >= 0) {
			// brackets
			assert left != null && right != null;
			final boolean isOpening = (bracketIndex & 0x1) == 0;
			final char bracket = brackets.charAt(bracketIndex), target = brackets.charAt(bracketIndex ^ 0x1);
			if (bracket == '<' || bracket == '>') { // only if from a generic parameter or argument
				final ASTElement parent = (usingLeftBracket ? left : right).parent();
				if (parent == null || parent instanceof ASTOperatorExpression)
					return;
			}
			int groups = 0;
			final TokenListStream tokenStream = tokens.stream();
			tokenStream.setTokenOffset(usingLeftBracket ? left : right);
			if (isOpening)
				tokenStream.getAndMoveForward();
			else
				tokenStream.getAndMoveBackward();
			while (true) {
				final Token t = isOpening ? tokenStream.getAndMoveForward() : tokenStream.getAndMoveBackward();
				if (t == null)
					break;
				final char c = t instanceof SymbolToken ? ((SymbolToken) t).symbol : '\0';
				if (c == bracket)
					groups++;
				else if (c == target)
					groups--;
				if (groups < 0) {
					viewer.setSelectedRange(isOpening ? bracket1 + 1 : t.absoluteRegionStart() + 1,
							Math.abs(t.absoluteRegionStart() - bracket1) - 1);
					return;
				}
			}
		} else {
			// not brackets
			if (left instanceof StringToken) {
				viewer.setSelectedRange(left.absoluteRegionStart(), left.regionLength());
				return;
			}
			if (right instanceof StringToken) {
				viewer.setSelectedRange(right.absoluteRegionStart(), right.regionLength());
				return;
			}
			if (right instanceof CommentToken) {
				new DefaultTextDoubleClickStrategy().doubleClicked(viewer);
				return;
				// final int i = offset - right.regionStart() - 2;
				// final String comment = ((CommentToken) right).comment;
				// int start, end;
				// for (start = i; start >= 0; start--) {
				// if (Character.isWhitespace(comment.codePointAt(start)))
				// break;
				// }
				// for (end = i; end >= 0 && end < comment.length(); end++) {
				// if (Character.isWhitespace(comment.codePointAt(end)))
				// break;
				// }
				// // start and end are now on whitespace or out of the string
				// viewer.setSelectedRange(right.regionStart() + 2 + start + 1, Math.max(end -
				// start - 1, 0));
				// return;
			}
			if (right != null)
				viewer.setSelectedRange(right.absoluteRegionStart(), right.regionLength());
			else if (left != null)
				viewer.setSelectedRange(left.absoluteRegionStart(), left.regionLength());
		}
	}
}
