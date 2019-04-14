package ch.njol.tome.eclipse;

import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.DocumentCommand;
import org.eclipse.jface.text.IAutoEditStrategy;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IRegion;

import ch.njol.tome.Constants;
import ch.njol.tome.compiler.Token;
import ch.njol.tome.compiler.Token.CodeGenerationToken;
import ch.njol.tome.compiler.Token.MultiLineCommentToken;
import ch.njol.tome.compiler.Token.SingleLineCommentToken;
import ch.njol.tome.compiler.Token.StringToken;
import ch.njol.tome.compiler.Token.SymbolToken;
import ch.njol.tome.eclipse.Plugin.DocumentData;

public class AutoEditStrategy implements IAutoEditStrategy {
	
	private final Editor editor;
	
	public AutoEditStrategy(final Editor editor) {
		this.editor = editor;
	}
	
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
					final String indent = getIndent(document, command.offset);
					
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
					} else if (t instanceof CodeGenerationToken && command.offset >= t.absoluteRegionStart() + Constants.CODE_GENERATION_START.length()
							&& command.offset < t.absoluteRegionEnd()) {
						// if breaking inside a code generation statement, make the new line one too
						command.text += Constants.CODE_GENERATION_START + " ";
					} else if (command.offset > 0 && document.getChar(command.offset - 1) == '{') {
						// if breaking after a '{', increase the indent by one, and move immediately following '}' to the line after that
						// also, if a '}' is missing, add one
						command.text += '\t';
						command.caretOffset = command.offset + command.text.length();
						command.shiftsCaret = false;
						if (document.getChar(command.offset) == '}')
							command.text += '\n' + indent;
						else if (countBrackets(data, '{', '}') > 0)
							command.text += '\n' + indent + '}';
					} else if (t instanceof StringToken && command.offset > t.absoluteRegionStart() && command.offset < t.absoluteRegionEnd()) {
						// if breaking inside a string, break the string into two lines and join them with a '+'
						// FIXME fix indentation when breaking in an already broken line
						char quote = t.getCode().charAt(0);
						command.text = quote + command.text + "\t+ " + quote;
					}
				} else if (command.text.equals("{")) {
					// don't do anything
				} else if (command.text.equals("}")) {
					// when closing a multi-line block (i.e. closing bracket on new line), correct indentation of closing bracket
					IRegion line = document.getLineInformationOfOffset(command.offset);
					if (document.get(line.getOffset(), command.offset - line.getOffset()).trim().isEmpty() // bracket is first non-whitespace on the line
							&& line.getOffset() > 0 // not on first line
							&& getIndent(document, command.offset).length() == getIndent(document, line.getOffset() - 1).length()) { // and previous line has same indentation as this one
						// TODO check if a new block was inserted, and indent the block instead of de-dent the '}'
						command.offset -= 1;
						command.length += 1;
					}
				} else if (command.text.equals("\"") || command.text.equals("'")) {
					final Token t = data.tokens.getTokenAt(command.offset, true);
					if (t instanceof StringToken && command.offset == t.absoluteRegionEnd() - 1 && t.getCode().charAt(0) == command.text.charAt(0)) {
						// if at the end of a string, close it (i.e. don't insert anything, just shift the caret)
						command.text = "";
						command.shiftsCaret = false;
						command.caretOffset = command.offset + 1;
					} else if (!(t instanceof StringToken) || command.offset <= t.absoluteRegionStart()) {
						// if not in a string, close the started string immediately
						command.shiftsCaret = false;
						command.caretOffset = command.offset + command.text.length();
						command.text += command.text;
					}
				} else if (Constants.MULTI_LINE_COMMENT_START.endsWith(command.text)) {
					// if starting a multi-line comment, end it
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
	
	/**
	 * @param data
	 * @param opening
	 * @param closing
	 * @return Positive for more opening than closing brackets, zero for equal amounts, and negative for more closing than opening brackets
	 */
	private static int countBrackets(DocumentData<?> data, char opening, char closing) {
		int count = 0;
		for (Token t : data.tokens) {
			if (t instanceof SymbolToken) {
				char s = ((SymbolToken) t).symbol;
				if (s == opening)
					count++;
				else if (s == closing)
					count--;
			}
		}
		return count;
	}
	
	private void autoInsertChar(final IDocument document, final DocumentCommand command,
			char startChar, char endChar) {
		if (possiblyOverrideAutoInsertedChar(document, command, startChar, endChar))
			return;
		// TODO don't do anything if the user action results in syntactically valid code
		command.caretOffset = command.offset + command.text.length();
		command.shiftsCaret = false;
		command.text += endChar;
	}
	
	private boolean possiblyOverrideAutoInsertedChar(final IDocument document, final DocumentCommand command,
			char startChar, char endChar) {
		return false;
	}
	
	private static String getIndent(final IDocument document, final int offset) throws BadLocationException {
		final int lineStart = document.getLineInformationOfOffset(offset).getOffset();
		final StringBuilder indent = new StringBuilder();
		for (int i = lineStart; i < offset && i < document.getLength(); i++) {
			final char c = document.getChar(i);
			if (c == '\t') { // Spaces cause issues with pretty comments, so just ignore them
				indent.append(c);
			} else {
				break;
			}
		}
		return indent.toString();
	}
	
//	private static String getBaseIndent() {
	// Or getExpectedIndent (take into account the start of the statement and all opened parentheses)
	// or maybe make it even simpler - just use the previous line's indent if it is not the start of a statement?
//		
//	}
	
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
