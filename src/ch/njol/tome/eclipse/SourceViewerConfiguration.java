package ch.njol.tome.eclipse;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.ITextDoubleClickStrategy;
import org.eclipse.jface.text.ITextHover;
import org.eclipse.jface.text.contentassist.ContentAssistant;
import org.eclipse.jface.text.contentassist.IContentAssistant;
import org.eclipse.jface.text.hyperlink.IHyperlinkDetector;
import org.eclipse.jface.text.presentation.IPresentationReconciler;
import org.eclipse.jface.text.presentation.PresentationReconciler;
import org.eclipse.jface.text.source.DefaultAnnotationHover;
import org.eclipse.jface.text.source.IAnnotationHover;
import org.eclipse.jface.text.source.ISourceViewer;

public class SourceViewerConfiguration extends org.eclipse.jface.text.source.SourceViewerConfiguration {
	
	final Editor editor;
	
	final ColorManager colorManager;
	
	private final TextHover textHover;
	
	private final PresentationDamagerRepairer presentationDamagerRepairer;
	private final PresentationReconciler presentationReconciler = new PresentationReconciler();

	private final HyperlinkDetector hyperlinkDetector;
	
	public SourceViewerConfiguration(final Editor editor, final ColorManager colorManager) {
		this.editor = editor;
		this.colorManager = colorManager;
		textHover = new TextHover(editor);
		presentationDamagerRepairer = new PresentationDamagerRepairer(editor, colorManager);
		presentationReconciler.setDamager(presentationDamagerRepairer, IDocument.DEFAULT_CONTENT_TYPE);
		presentationReconciler.setRepairer(presentationDamagerRepairer, IDocument.DEFAULT_CONTENT_TYPE);
		hyperlinkDetector = new HyperlinkDetector(editor);
	}
	
	// reconciler - runs repeatedly in the background (can be used to run the parts
	// of the compiler not needed for syntax highlighting)
	
	// private final class BrokkrReconcilingStrategy implements
	// IReconcilingStrategy, IReconcilingStrategyExtension {
	// @Override
	// public void setDocument(@SuppressWarnings("null") final IDocument doc) {}
	//
	// @Override
	// public void reconcile(@SuppressWarnings("null") final IRegion region) {}
	//
	// @Override
	// public void reconcile(@SuppressWarnings("null") final DirtyRegion
	// dirtyRegion, @SuppressWarnings("null") final IRegion subRegion) {}
	//
	// @Override
	// public void setProgressMonitor(@Nullable final IProgressMonitor monitor) {}
	//
	// @Override
	// public void initialReconcile() {}
	// }
	
	// BrokkrReconcilingStrategy strategy = new BrokkrReconcilingStrategy();
	
	// private final IReconciler reconciler = new MonoReconciler(strategy, true);
	
	// @Override
	// public IReconciler getReconciler(@Nullable final ISourceViewer sourceViewer)
	// {
	// return reconciler;
	// }
	
	// presentation reconciler - updates the presentation of the source code when it
	// is changed
	
	@Override
	public @NonNull IPresentationReconciler getPresentationReconciler(final ISourceViewer sourceViewer) {
		return presentationReconciler;
	}
	
	// other stuff
	
	// <=== annotations are the icons on the left vertical bar
	@Override
	public @NonNull IAnnotationHover getAnnotationHover(final ISourceViewer sourceViewer) {
		return new DefaultAnnotationHover(false);
	}
	
	@Override
	public ITextDoubleClickStrategy getDoubleClickStrategy(@Nullable final ISourceViewer sourceViewer,
			@Nullable final String contentType) {
		return new DoubleClickStrategy(editor);
	}
	
	@Override
	public IHyperlinkDetector[] getHyperlinkDetectors(@Nullable final ISourceViewer sourceViewer) {
		return new IHyperlinkDetector[] {hyperlinkDetector};
	}
	
	@Override
	public ITextHover getTextHover(@Nullable final ISourceViewer sourceViewer, @Nullable final String contentType) {
		return textHover;
	}
	
	@Override
	public @NonNull IContentAssistant getContentAssistant(final ISourceViewer sourceViewer) {
		final ContentAssistant ca = new ContentAssistant();
		ca.setContentAssistProcessor(new ContentAssistProcessor(editor), IDocument.DEFAULT_CONTENT_TYPE);
		ca.enableAutoActivation(true);
		ca.setAutoActivationDelay(0);
		ca.setInformationControlCreator(getInformationControlCreator(sourceViewer));
		return ca;
	}
	
}
