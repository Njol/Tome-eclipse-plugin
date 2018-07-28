package ch.njol.tome.eclipse.handlers;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.ITextSelection;
import org.eclipse.jface.text.TextSelection;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.handlers.HandlerUtil;

import ch.njol.tome.eclipse.DocumentReader;
import ch.njol.tome.eclipse.Editor;
import ch.njol.tome.eclipse.Plugin.DocumentData;

public class ToggleCommentHandler extends AbstractHandler {
	
	@Override
	public @Nullable Object execute(final ExecutionEvent event) throws ExecutionException {
		final IEditorPart activeEditor = HandlerUtil.getActiveEditorChecked(event);
		if (!(activeEditor instanceof Editor))
			throw new ExecutionException("Invalid edtor type " + activeEditor.getClass());
		final Editor editor = (Editor) activeEditor;
		final DocumentData<?> data = editor.getData();
		if (data == null)
			return null;
		final IDocument document = ((DocumentReader) data.reader).document;
		final ISelection selection = editor.getSelectionProvider().getSelection();
		if (!(selection instanceof ITextSelection))
			return null;
		try {
			final ITextSelection textSelection = (ITextSelection) selection;
			final int startOffset = document.getLineOffset(textSelection.getStartLine());
			final int endOffset = document.getLineOffset(textSelection.getEndLine()) + document.getLineLength(textSelection.getEndLine());
			final String lines = document.get(startOffset, endOffset - startOffset);
			
			final boolean adding = Pattern.compile("^[ \\t]*[^# \\t]", Pattern.MULTILINE).matcher(lines).find();
			final Matcher lineStart = Pattern.compile("^[ \t]*", Pattern.MULTILINE).matcher(lines);
			int lastEnd = 0;
			final StringBuilder result = new StringBuilder();
			while (lineStart.find()) {
				if (adding) { // add to the beginning of lines
					result.append(lines.substring(lastEnd, lineStart.start()));
					result.append("#");
					lastEnd = lineStart.start();
				} else { // remove from after whitespace too
					assert lines.charAt(lineStart.end()) == '#';
					result.append(lines.substring(lastEnd, lineStart.end()));
					lastEnd = lineStart.end() + 1;
				}
			}
			result.append(lines.substring(lastEnd));
			document.replace(startOffset, endOffset - startOffset, result.toString());
			editor.getSelectionProvider().setSelection(new TextSelection(textSelection.getOffset() + (adding ? 1 : -1),
					textSelection.getLength() + (result.length() - lines.length()) - (adding ? 1 : -1)));
		} catch (final BadLocationException e) {
			throw new ExecutionException("Invalid selection", e);
		}
		return null;
	}
	
}
