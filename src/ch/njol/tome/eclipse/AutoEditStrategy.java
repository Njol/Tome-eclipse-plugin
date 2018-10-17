package ch.njol.tome.eclipse;

import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.DocumentCommand;
import org.eclipse.jface.text.IAutoEditStrategy;
import org.eclipse.jface.text.IDocument;

import ch.njol.tome.Constants;
import ch.njol.tome.compiler.Token;
import ch.njol.tome.compiler.Token.MultiLineCommentToken;
import ch.njol.tome.compiler.Token.SingleLineCommentToken;
import ch.njol.tome.eclipse.Plugin.DocumentData;

public class AutoEditStrategy implements IAutoEditStrategy {
	
	private final Editor editor;
	
	public AutoEditStrategy(final Editor editor) {
		this.editor = editor;
	}
	
	// TODO make smarter:
	// - if breaking a single-line comment, make the second line commented
	// - in multi-line comments, make comments pretty
	// - enter after starting a multi-line comment closes it
	// - MAYBE auto complete brackets/strings (if done properly!)
	@Override
	public void customizeDocumentCommand(final IDocument document, final DocumentCommand command) {
		final DocumentData<?> data = editor.getData();
		if (data == null)
			return;
		try {
			// something inserted or typed without replacing anything
			if (command.length == 0 && command.shiftsCaret && command.caretOffset == -1) {
				final String originalText = command.text;
				
				// enter pressed
				if (command.text.equals("\n") || command.text.equals("\r\n") || command.text.equals("\r")) {
					final String indent = getIndent(document, command);
					
					// add indentation to next line
					command.text += indent;
					
					final Token t = data.tokens.getTokenAt(command.offset, true);
					if (t instanceof MultiLineCommentToken && command.offset >= t.absoluteRegionStart() + Constants.MULTI_LINE_COMMENT_START.length()
							&& command.offset <= t.absoluteRegionEnd() - Constants.MULTI_LINE_COMMENT_END.length()) {
						// if inside a multi-line comment, prettify it (TODO don't prettify if it isn't already?)
						command.text += " " + Constants.MULTI_LINE_COMMENT_PRETTIFY_CHARACTER + " ";
						
						if (t.toString().equals(Constants.MULTI_LINE_COMMENT_START + Constants.MULTI_LINE_COMMENT_END)) {
							command.caretOffset = command.offset + command.text.length();
							command.shiftsCaret = false;
							command.text += originalText + indent + " ";
						}
					} else if (t instanceof SingleLineCommentToken && command.offset >= t.absoluteRegionStart() + Constants.SINGLE_LINE_COMMENT_START.length()
							&& command.offset < t.absoluteRegionEnd()) {
						// if breaking inside a single-line comment, make the new line a comment too
						command.text += Constants.SINGLE_LINE_COMMENT_START + " ";
					}
				}
				
				// if starting a multi-line comment, end it
				if (Constants.MULTI_LINE_COMMENT_START.endsWith(command.text)) {
					final int remainingLength = Constants.MULTI_LINE_COMMENT_START.length() - command.text.length();
					if (matches(document, command.offset - remainingLength, Constants.MULTI_LINE_COMMENT_START, remainingLength)) {
						command.caretOffset = command.offset + command.text.length();
						command.shiftsCaret = false;
						command.text += Constants.MULTI_LINE_COMMENT_END;
					}
				}
			}
		} catch (final BadLocationException e) {
			e.printStackTrace();
		}
	}
	
	private static String getIndent(final IDocument document, final DocumentCommand command) throws BadLocationException {
		final int lineStart = document.getLineOffset(document.getLineOfOffset(command.offset));
		final StringBuilder indent = new StringBuilder();
		for (int i = lineStart; i < command.offset && i < document.getLength(); i++) {
			final char c = document.getChar(i);
			if (c == '\t') { // Spaces cause issues with pretty comments, so just ignore them
				indent.append(c);
			} else {
				break;
			}
		}
		return indent.toString();
	}
	
	private static boolean matches(final IDocument document, final int start, final String match) throws BadLocationException {
		return matches(document, start, match, match.length());
	}
	
	private static boolean matches(final IDocument document, final int start, final String match, final int length) throws BadLocationException {
		if (length == 0)
			return true;
		final String s = getText(document, start, length);
		return s != null && match.substring(0, length).equals(s);
	}
	
	private static @Nullable String getText(final IDocument document, final int start, final int length) throws BadLocationException {
		if (start < 0 || start + length > document.getLength())
			return null;
		return document.get(start, length);
	}
	
}
