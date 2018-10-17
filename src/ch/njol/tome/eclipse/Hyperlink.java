package ch.njol.tome.eclipse;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.runtime.Adapters;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jface.text.IRegion;
import org.eclipse.jface.text.ITextOperationTarget;
import org.eclipse.jface.text.ITextViewer;
import org.eclipse.jface.text.Region;
import org.eclipse.jface.text.hyperlink.IHyperlink;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.ide.IDE;

import ch.njol.tome.ast.ASTElement;
import ch.njol.tome.ast.ASTElementPart;

final class Hyperlink implements IHyperlink {
	private final int start;
	private final int length;
	private final ASTElementPart target;
	private @Nullable final String kind;
	
	public Hyperlink(final ASTElementPart link, final ASTElementPart target) {
		this(link.absoluteRegionStart(), link.regionLength(), target, null);
	}
	
	public Hyperlink(final int start, final int length, final ASTElementPart target, @Nullable final String kind) {
		this.start = start;
		this.length = length;
		this.target = target;
		this.kind = kind;
	}
	
	@Override
	public void open() {
		try {
			final ASTElement e = target instanceof ASTElement ? (ASTElement) target : target.parent();
			if (e == null)
				return;
			final IFile file = Plugin.getFile(e);
			if (file == null)
				return;
			final IEditorPart ep = IDE
					.openEditor(PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage(), file);
			if (target.parent() != null) { // select linked element, except if the whole file was linked
				final ITextOperationTarget textOperationTarget = Adapters.adapt(ep, ITextOperationTarget.class,
						true);
				final ITextViewer textViewer = Adapters.adapt(textOperationTarget, ITextViewer.class, true);
				if (textViewer != null) {
					textViewer.setSelectedRange(target.linkStart(), target.linkLength());
					textViewer.revealRange(target.linkStart(), target.linkLength());
				}
			}
		} catch (final PartInitException e) {}
	}
	
	@Override
	public @Nullable String getTypeLabel() {
		return null;
	}
	
	@Override
	public @Nullable String getHyperlinkText() {
		return kind;
	}
	
	@Override
	public IRegion getHyperlinkRegion() {
		return new Region(start, length);
	}
	
}
