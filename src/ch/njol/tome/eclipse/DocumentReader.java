package ch.njol.tome.eclipse;

import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;

import ch.njol.tome.compiler.BrokkrReader;

public class DocumentReader implements BrokkrReader {
	
	public final IDocument document;
	
	public DocumentReader(final IDocument document) {
		this.document = document;
	}
	
	int offset = 0;
	
	@Override
	public int getOffset() {
		return offset;
	}
	
	@Override
	public void setOffset(final int offset) {
		this.offset = offset;
	}
	
	@Override
	public boolean isBeforeStart() {
		return offset <= 0;
	}
	
	@Override
	public boolean isAfterEnd() {
		return offset >= document.getLength();
	}
	
	@Override
	public int getLine(final int offset) {
		try {
			return document.getLineOfOffset(offset);
		} catch (final BadLocationException e) {
			return -1;
		}
	}
	
	@Override
	public int peekNext(final int delta) {
		try {
			return document.getChar(offset + delta);
		} catch (final BadLocationException e) {
			return -1;
		}
	}
	
	@Override
	public int next() {
		try {
			final char c = document.getChar(offset);
			offset++;
			return c;
		} catch (final BadLocationException e) {
			return -1;
		}
	}
	
	@Override
	public int getColumn(final int offset) {
		try {
			final int line = document.getLineOfOffset(offset);
			return document.getLineOffset(line) - offset;
		} catch (final BadLocationException e) {
			return -1;
		}
	}
	
	@Override
	public String getLineTextAtOffset(final int offset) {
		try {
			final int line = document.getLineOfOffset(offset);
			return "" + document.get(document.getLineOffset(line), document.getLineLength(line));
		} catch (final BadLocationException e) {
			return "";
		}
	}
	
	@Override
	public int getLineStart(final int offset) {
		try {
			final int line = document.getLineOfOffset(offset);
			return document.getLineOffset(line);
		} catch (final BadLocationException e) {
			return -1;
		}
	}
	
	@Override
	public int getLineEnd(final int offset) {
		try {
			final int line = document.getLineOfOffset(offset);
			return document.getLineOffset(line) + document.getLineLength(line);
		} catch (final BadLocationException e) {
			return -1;
		}
	}
	
	@Override
	public String getText(final int start, final int end) {
		try {
			return document.get(start, end - start);
		} catch (final BadLocationException e) {
			return "";
		}
	}
	
	@Override
	public void back() {
		if (offset > 0)
			offset--;
	}
	
	@Override
	public void reset() {
		offset = 0;
	}
	
	@Override
	public int getLength() {
		return document.getLength();
	}
	
	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + document.hashCode();
		return result;
	}
	
	@Override
	public boolean equals(@Nullable final Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		final DocumentReader other = (DocumentReader) obj;
		if (!document.equals(other.document))
			return false;
		return true;
	}
	
}
