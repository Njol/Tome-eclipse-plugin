package ch.njol.tome.eclipse;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IResource;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jface.text.DocumentEvent;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.ITextInputListener;
import org.eclipse.jface.text.ITextListener;
import org.eclipse.jface.text.TextEvent;
import org.eclipse.jface.text.source.IAnnotationAccess;
import org.eclipse.jface.text.source.ISourceViewer;
import org.eclipse.jface.text.source.IVerticalRuler;
import org.eclipse.jface.viewers.DoubleClickEvent;
import org.eclipse.jface.viewers.IDoubleClickListener;
import org.eclipse.jface.viewers.ILabelProvider;
import org.eclipse.jface.viewers.ILabelProviderListener;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.ISelectionChangedListener;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.ITreeContentProvider;
import org.eclipse.jface.viewers.LabelProviderChangedEvent;
import org.eclipse.jface.viewers.SelectionChangedEvent;
import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.ui.editors.text.TextFileDocumentProvider;
import org.eclipse.ui.texteditor.AbstractDecoratedTextEditor;
import org.eclipse.ui.texteditor.DefaultMarkerAnnotationAccess;
import org.eclipse.ui.views.contentoutline.IContentOutlinePage;

import ch.njol.tome.Constants;
import ch.njol.tome.ast.ASTElement;
import ch.njol.tome.ast.members.ASTAttributeDeclaration;
import ch.njol.tome.ast.members.ASTConstructor;
import ch.njol.tome.ast.members.ASTTemplate;
import ch.njol.tome.ast.toplevel.ASTClassDeclaration;
import ch.njol.tome.ast.toplevel.ASTExtensionDeclaration;
import ch.njol.tome.ast.toplevel.ASTInterfaceDeclaration;
import ch.njol.tome.ast.toplevel.ASTModuleDeclaration;
import ch.njol.tome.eclipse.Plugin.DocumentData;
import ch.njol.tome.moduleast.ASTModule;
import ch.njol.tome.moduleast.ASTModule.ASTImport;
import ch.njol.tome.moduleast.ASTModuleFileElement.ListElement;
import ch.njol.tome.moduleast.ASTModuleFileElement.MapElement;

// TODO highlight matching brackets?
public class Editor extends AbstractDecoratedTextEditor implements ITextListener {
	
	private final ColorManager colorManager;
	
	public Editor() {
		colorManager = new ColorManager();
		setSourceViewerConfiguration(new SourceViewerConfiguration(this, colorManager));
		setDocumentProvider(new TextFileDocumentProvider());
		setKeyBindingScopes(new String[] {Plugin.ID + ".contexts.editorScope"});
//		setWordWrap(true); // TODO figure out where/when to set this or wait for eclipse to re-add the option
	}
	
	public @Nullable DocumentData<?> getData() {
		Builder.checkForFistRun();
		final IResource resource = getEditorInput().getAdapter(IResource.class);
		if (!(resource instanceof IFile))
			return null;
		final IFile file = (IFile) resource;
		final ISourceViewer sourceViewer = getSourceViewer();
		if (sourceViewer == null)
			return null;
		final IDocument document = sourceViewer.getDocument();
		if (document == null)
			return null;
		final DocumentData<?> data = Plugin.getData(file);
		if (data != null) {
			if (!(data.reader instanceof DocumentReader) || ((DocumentReader) data.reader).document != document)
				data.update(new DocumentReader(document));
			return data;
		}
		return Plugin.getData(file, new DocumentReader(document), Constants.MODULE_FILE_EXTENSION.equals(file.getFileExtension()) ? Plugin.moduleParser : Plugin.sourceFileParser(file));
	}
	
	@Override
	protected ISourceViewer createSourceViewer(final Composite parent, final IVerticalRuler ruler, final int styles) {
		final ISourceViewer sv = super.createSourceViewer(parent, ruler, styles);
		assert sv != null;
		sv.addTextInputListener(new ITextInputListener() {
			@Override
			public void inputDocumentChanged(final IDocument oldInput, final IDocument newInput) {
				final DocumentData<?> data = getData();
				final IResource r = getEditorInput().getAdapter(IResource.class);
				if (data != null && r != null)
					Builder.updateMarkersAndLink(r, data);
				outlineChanged();
			}
			
			@Override
			public void inputDocumentAboutToBeChanged(final IDocument oldInput, final IDocument newInput) {}
		});
		sv.addTextListener(this);
		return sv;
	}
	
	// a document listener doesn't work because it is called after the presentation is updated, but this must run first.
	@Override
	public void textChanged(final TextEvent textEvent) {
		final DocumentEvent event = textEvent.getDocumentEvent();
		if (event == null)
			return;
		
		final DocumentData<?> data = getData();
		if (data != null) {
//			data.update(event.getOffset(), Math.max(event.getLength(), event.getText().length()));
			data.update();
			outlineChanged();
			AsyncBuilder.notifyChange(data);
		}
	}
	
//	private void updateMarkers() {
//		final DocumentData<?> data = getData();
//		if (data != null) {
//			final IResource r = getEditorInput().getAdapter(IResource.class);
//			if (r != null) {
//				Builder.deleteMarkers(r);
//				Builder.createSyntaxMarkers(data, r);
//				try {
//					Builder.link(r, data);
//				} catch (CoreException e) {}
//			}
//		}
//		// TODO make incremental
////		try {
////			r.getWorkspace().run(new ICoreRunnable() {
////				@Override
////				public void run(final @Nullable IProgressMonitor monitor) throws CoreException {
////					// make markers and annotations (remove old ones first)
//////					((IAnnotationModelExtension) editor.getAnnotationModel()).removeAllAnnotations();
//////					final Iterator<Annotation> iter = ((IAnnotationModelExtension2) editor.getAnnotationModel()).getAnnotationIterator(dirtyRegion.getOffset(), dirtyRegion.getLength(), true, true);
//////					while (iter.hasNext())
//////						editor.getAnnotationModel().removeAnnotation(iter.next());
////					try {
////						r.deleteMarkers(IMarker.PROBLEM, true, IResource.DEPTH_INFINITE);
////						r.deleteMarkers(IMarker.TASK, true, IResource.DEPTH_INFINITE);
////					} catch (final CoreException ex) {}
////					// first remove annotations and markers that do not exist any more
//////						final ArrayList<ParseError> errors = new ArrayList<>(data.ast.fatalParseErrors());
//////						final Iterator<Annotation> iter = editor.getAnnotationModel().getAnnotationIterator();
//////						while (iter.hasNext()) {
//////							final Annotation a = iter.next();
//////							if (!(a instanceof MarkerAnnotation))
//////								continue;
//////							final IMarker m = ((MarkerAnnotation) a).getMarker();
//////							final int start = m.getAttribute(IMarker.CHAR_START, -1), end = m.getAttribute(IMarker.CHAR_END, -1);
//////							final String message = m.getAttribute(IMarker.MESSAGE, null);
//////							boolean exists = false;
//////							for (int i = 0; i < errors.size(); i++) {
//////								final ParseError e = errors.get(i);
//////								if (e.start == start && e.start + e.length == end && e.expected.equals(message)) {
//////									exists = true;
//////									errors.remove(i);
//////									break;
//////								}
//////							}
//////							if (!exists) {
//////								editor.getAnnotationModel().removeAnnotation(a);
//////								try {
//////									m.delete();
//////								} catch (final CoreException ex) {}
//////							}
//////						}
////
////					// then add new errors
////					for (final ParseError e : data.ast.fatalParseErrors()) {//errors) {
////						try {
////							final Map<String, Object> attributes = new HashMap<>();
////							attributes.put("" + IMarker.SEVERITY, IMarker.SEVERITY_ERROR);
////							attributes.put("" + IMarker.CHAR_START, e.start);
////							attributes.put("" + IMarker.CHAR_END, e.start + e.length);
////							attributes.put("" + IMarker.MESSAGE, e.message);
////							attributes.put("" + IMarker.LINE_NUMBER, data.reader.getLine(e.start));
////							attributes.put("" + IMarker.PRIORITY, IMarker.PRIORITY_HIGH);
////							attributes.put("" + IMarker.USER_EDITABLE, false);
//////								MarkerUtilities.createMarker(r, attributes, IMarker.PROBLEM);
////							final IMarker m = r.createMarker(IMarker.PROBLEM);
////							m.setAttributes(attributes);
//////								final MarkerAnnotation a = new MarkerAnnotation(DefaultMarkerAnnotationAccess.ERROR_SYSTEM_IMAGE, m);
//////								editor.getAnnotationModel().addAnnotation(a, new Position(e.start, e.length));
////						} catch (final CoreException ex) {}
////					}
////
////					for (final Token t : data.tokens.tokens) {
////						if (t instanceof CommentToken) {
////							final Matcher m = commentTasks.matcher(((CommentToken) t).comment);
////							while (m.find()) {
////								final int start = m.start();
////								final Matcher m2 = Pattern.compile("$", Pattern.MULTILINE).matcher(((CommentToken) t).comment);
////								m2.find(start);
////								final int end = m2.end();
////								try {
////									final Map<String, Object> attributes = new HashMap<>();
////									attributes.put("" + IMarker.SEVERITY, IMarker.SEVERITY_INFO);
////									attributes.put("" + IMarker.CHAR_START, t.regionStart() + start);
////									attributes.put("" + IMarker.CHAR_END, t.regionStart() + end);
////									attributes.put("" + IMarker.MESSAGE, "" + ((CommentToken) t).comment.substring(start, end));
////									attributes.put("" + IMarker.LINE_NUMBER, data.reader.getLine(start));
////									attributes.put("" + IMarker.PRIORITY, "FIXME".equals(m.group(1)) || "BUG".equals(m.group(1)) ? IMarker.PRIORITY_HIGH : IMarker.PRIORITY_NORMAL);
////									attributes.put("" + IMarker.USER_EDITABLE, false);
//////										MarkerUtilities.createMarker(r, attributes, IMarker.PROBLEM);
////									final IMarker marker = r.createMarker(IMarker.TASK);
////									marker.setAttributes(attributes);
//////										final MarkerAnnotation a = new MarkerAnnotation(DefaultMarkerAnnotationAccess.ERROR_SYSTEM_IMAGE, m);
//////										editor.getAnnotationModel().addAnnotation(a, new Position(e.start, e.length));
////								} catch (final CoreException ex) {}
////							}
////						}
////					}
////				}
////			}, null, IWorkspace.AVOID_UPDATE, null);
////		} catch (final CoreException ex) {}
//	}
	
	@Override
	protected IAnnotationAccess createAnnotationAccess() {
		return new DefaultMarkerAnnotationAccess();
	}
	
	@Override
	public void dispose() {
		colorManager.dispose();
		super.dispose();
	}
	
	final Set<ILabelProviderListener> labelProviderListeners = new HashSet<>();
	
	// TODO remember expanded/closed parts (or don't change everything around on a change)
	// TODO expand single(/double?) class/interface (i.e. expand if it's the only type in the file)
	public void outlineChanged() {
		final TreeViewer tv = contentOutlinePage.getTreeViewer();
		if (tv != null && tv.getControl() != null && !tv.getControl().isDisposed())
			tv.setInput(getData());
		final LabelProviderChangedEvent event = new LabelProviderChangedEvent(labelProvider);
		for (final ILabelProviderListener l : labelProviderListeners) {
			l.labelProviderChanged(event);
		}
	}
	
	private final static Set<Class<? extends ASTElement>> inOutline = new HashSet<>();
	static {
		inOutline.addAll(Arrays.asList(ASTModuleDeclaration.class, //
				ASTInterfaceDeclaration.class, ASTClassDeclaration.class, /*EnumDeclaration.class,*/ ASTExtensionDeclaration.class, //
				ASTAttributeDeclaration.class, ASTConstructor.class, ASTTemplate.class, //EnumElement.class, //
				// module stuff:
				ASTModule.class, ASTImport.class, MapElement.class, ListElement.class));
	}
	
	private final static boolean isInOutline(final Object e) {
		return inOutline.contains(e.getClass());
	}
	
	private final ILabelProvider labelProvider = new ILabelProvider() {
		@Override
		public void addListener(final ILabelProviderListener listener) {
			labelProviderListeners.add(listener);
		}
		
		@Override
		public void removeListener(final ILabelProviderListener listener) {
			labelProviderListeners.remove(listener);
		}
		
		@Override
		public boolean isLabelProperty(final Object element, final String property) {
			return true;
		}
		
		@Override
		public void dispose() {}
		
		@Override
		public @NonNull String getText(final Object element) {
			return "" + element;
		}
		
		@Override
		public @Nullable Image getImage(final Object element) {
			if (element instanceof ASTModuleDeclaration)
				return Images.moduleIcon.get();
//			if (element instanceof Imports)
//				return Images.importsIcon.get();
//			if (element instanceof Import)
//				return Images.importIcon.get();
			if (element instanceof ASTClassDeclaration)
				return Images.classIcon.get();
			if (element instanceof ASTInterfaceDeclaration)
				return Images.interfaceIcon.get();
			if (element instanceof ASTConstructor)
				return Images.constructorIcon.get();
			if (element instanceof ASTExtensionDeclaration)
				return Images.extensionIcon.get();
//			if (element instanceof EnumDeclaration)
//				return Images.enumIcon.get();
//			if (element instanceof EnumElement)
//				return Images.enumElementIcon.get();
			if (element instanceof ASTTemplate)
				return Images.templateIcon.get();
			if (element instanceof ASTAttributeDeclaration) {
				final ASTAttributeDeclaration attr = (ASTAttributeDeclaration) element;
				return (attr.modifiers.isStatic ? Images.staticAttributeIcon : Images.instanceAttributeIcon)//
						.withOverlays(attr.modifiers.override ? attr.modifiers.overridden() == null ? Images.overlay_renamed : null : Images.overlay_new);
			}
			return null;
		}
	};
	
	private final static @Nullable ASTElement getSelectedElement(final @Nullable ISelection selection) {
		if (selection instanceof IStructuredSelection && ((IStructuredSelection) selection).size() == 1) {
			final Object o = ((IStructuredSelection) selection).getFirstElement();
			return o instanceof ASTElement ? (ASTElement) o : null;
		}
		return null;
	}
	
	private final class ContentOutlinePage extends org.eclipse.ui.views.contentoutline.ContentOutlinePage {
		@Override
		public @Nullable TreeViewer getTreeViewer() {
			return super.getTreeViewer();
		}
		
		@Override
		public void createControl(final Composite parent) {
			super.createControl(parent);
			
			final TreeViewer treeViewer = getTreeViewer();
			assert treeViewer != null; // created by super.createControl call above
			
			treeViewer.setContentProvider(new ITreeContentProvider() {
				@SuppressWarnings("null")
				@Override
				public Object[] getElements(final Object inputElement) {
					return ((DocumentData<?>) inputElement).ast.parts().stream().filter(e -> isInOutline(e)).toArray();
				}
				
				@SuppressWarnings("null")
				@Override
				public Object[] getChildren(final Object element) {
					return ((ASTElement) element).parts().stream().filter(e -> isInOutline(e)).toArray();
				}
				
				@Override
				public @Nullable Object getParent(final Object element) {
					return ((ASTElement) element).parent();
				}
				
				@Override
				public boolean hasChildren(final Object element) {
					return ((ASTElement) element).parts().stream().anyMatch(e -> isInOutline(e));
				}
			});
			
			treeViewer.setLabelProvider(labelProvider);
			
			treeViewer.addSelectionChangedListener(new ISelectionChangedListener() {
				@Override
				public void selectionChanged(final SelectionChangedEvent event) {
					final ASTElement e = getSelectedElement(event.getSelection());
					if (e != null) {
						getSourceViewer().setSelectedRange(e.linkStart(), e.linkLength());
						getSourceViewer().revealRange(e.linkStart(), e.linkLength());
					}
				}
			});
			
			treeViewer.addDoubleClickListener(new IDoubleClickListener() {
				@Override
				public void doubleClick(final DoubleClickEvent event) {
					final ASTElement e = getSelectedElement(event.getSelection());
					if (e != null)
						treeViewer.setExpandedState(e, !treeViewer.getExpandedState(e));
				}
			});
			
			treeViewer.setInput(getData());
		}
	}
	
	private final ContentOutlinePage contentOutlinePage = new ContentOutlinePage();
	
	@SuppressWarnings("unchecked")
	@Override
	public <T> @Nullable T getAdapter(final Class<T> adapter) {
		if (adapter == IContentOutlinePage.class)
			return (T) contentOutlinePage;
		return super.getAdapter(adapter);
	}
	
}
