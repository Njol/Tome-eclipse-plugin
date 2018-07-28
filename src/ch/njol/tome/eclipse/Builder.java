package ch.njol.tome.eclipse;

import static ch.njol.tome.Constants.*;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IResourceDelta;
import org.eclipse.core.resources.IResourceDeltaVisitor;
import org.eclipse.core.resources.IResourceProxy;
import org.eclipse.core.resources.IResourceProxyVisitor;
import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.resources.IncrementalProjectBuilder;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.ICoreRunnable;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.core.runtime.SubMonitor;
import org.eclipse.jdt.annotation.Nullable;

import ch.njol.tome.ast.ASTDocument;
import ch.njol.tome.ast.ASTElement;
import ch.njol.tome.ast.ASTTopLevelElements.ASTSourceFile;
import ch.njol.tome.compiler.Linker;
import ch.njol.tome.compiler.LinkerError;
import ch.njol.tome.compiler.SourceReader;
import ch.njol.tome.compiler.StringReader;
import ch.njol.tome.compiler.Token;
import ch.njol.tome.compiler.Token.CommentToken;
import ch.njol.tome.compiler.Token.WordOrSymbols;
import ch.njol.tome.compiler.TokenList;
import ch.njol.tome.eclipse.Plugin.DocumentData;
import ch.njol.tome.moduleast.ASTModule;
import ch.njol.tome.parser.ParseError;

public class Builder extends IncrementalProjectBuilder {
	
	private static volatile boolean firstRun = true;
	
	// TODO store compiler results (or parts thereof) and load those on startup (in build below)
	// (clean() should obviously delete the cached data)
	static void checkForFistRun() {
		if (!firstRun)
			return;
		synchronized (Builder.class) {
			if (!firstRun)
				return;
			firstRun = false;
			try {
				for (final IProject p : ResourcesPlugin.getWorkspace().getRoot().getProjects())
					p.build(FULL_BUILD, null);
			} catch (final CoreException e) {
				e.printStackTrace();
			}
		}
		
	}
	
//	@Override
//	protected void startupOnInitialize() {
//		super.startupOnInitialize();
//		checkForFistRun(); // can this be problematic?
//	}
	
	@Override
	protected void clean(@Nullable final IProgressMonitor monitor) throws CoreException {
		deleteMarkers(getProject());
		Plugin.removeAllProjectData(getProject());
	}
	
	@Override
	protected IProject @Nullable [] build(final int kind, @Nullable final Map<String, String> args, @Nullable final IProgressMonitor monitor) throws CoreException {
		final SubMonitor sm = SubMonitor.convert(monitor, LANGUAGE_NAME + " Build", 100);
		final SubMonitor findChangesMonitor = sm.split(10);
		findChangesMonitor.setWorkRemaining(100);
		
		// files to be rebuilt
		final Set<IFile> filesToRebuild = new HashSet<>();
		
		// find changed files
		final IResourceDelta delta = getDelta(getProject());
		if ((kind == INCREMENTAL_BUILD || kind == AUTO_BUILD) && delta != null) {
			// incemental build
			delta.accept(new IResourceDeltaVisitor() {
				@Override
				public boolean visit(final @Nullable IResourceDelta delta) throws CoreException {
					if (delta == null)
						return false;
					final IResource res = delta.getResource();
					if (res instanceof IFile) {
						switch (delta.getKind()) {
							case IResourceDelta.ADDED:
							case IResourceDelta.CHANGED: {
								filesToRebuild.add((IFile) delta.getResource());
								break;
							}
							case IResourceDelta.REMOVED: {
								final DocumentData<?> data = Plugin.removeData(delta.getFullPath()); // also invalidates the AST and thus also removes links to/from modules
								if (data != null && data.ast instanceof ASTSourceFile)
									assert ((ASTSourceFile) data.ast).identifier.equals("" + delta.getFullPath().toString());
							}
						}
					}
					return true;
				}
			});
		} else {
			// full build: first clear all caches, and then rebuild all files (module files first)
			clean(findChangesMonitor.split(50));
			getProject().accept(new IResourceProxyVisitor() {
				@Override
				public boolean visit(final @Nullable IResourceProxy proxy) throws CoreException {
					if (proxy == null)
						return false;
					if (proxy.getName().endsWith(".brok") || proxy.getName().endsWith("." + MODULE_FILE_EXTENSION)) {
						final IResource res = proxy.requestResource();
						if (res instanceof IFile) {
							filesToRebuild.add((IFile) res);
						}
					}
					return true;
				}
			}, 0);
		}
		
		findChangesMonitor.done();
		final SubMonitor parseMonitor = sm.split(50);
		parseMonitor.setWorkRemaining(filesToRebuild.size());
		
		// parse changed files
		for (final IFile file : filesToRebuild) {
			final String doc = readFile(file);
			if (doc == null)
				continue;
			
			final DocumentData<?> data = Plugin.getData(file.getFullPath(), new StringReader(doc), file.getName().endsWith("." + MODULE_FILE_EXTENSION) ? Plugin.moduleParser : Plugin.sourceFileParser(file));
			createSyntaxMarkers(data, file);
			
			if (parseMonitor.isCanceled())
				throw new CancellationException();
			parseMonitor.worked(1);
		}
		parseMonitor.done();
		
		// fix module links
		fixModules();
		
		// link all ASTLinks
		// FIXME only link updated values (by registering an InvalidateListener on the ASTs?)
		// TODO 1: when a link is invalidated, immediately (actually, better a bit delayed) try to re-link and make an error marker on failure
		// TODO 2: whenever ANY file changes, revalidate ALL failed links to check whether the links work now and remove their error markers if so
		// (not too bad as there are usually only very few such links - who has thousands of invalid references?)
		linkAll(sm.split(40));
		
		sm.done();
		return null;
	}
	
	public static void updateMarkersAndLink(final IResource file, final DocumentData<?> data) {
		deleteMarkers(file);
		createSyntaxMarkers(data, file);
		fixModules();
		try {
			if (data.ast instanceof ASTModule)
				linkAll(null);
			else
				link(file, data);
		} catch (final CoreException e) {}
	}
	
	/**
	 * Fixes all module &lt;-> source file references.
	 */
	private static void fixModules() {
		for (final Entry<IPath, DocumentData<?>> e : Plugin.getAllData()) {
			final ASTElement ast = e.getValue().ast;
			if (ast instanceof ASTSourceFile)
				((ASTSourceFile) ast).updateModule();
		}
	}
	
	private final static void linkAll(final @Nullable IProgressMonitor monitor) throws CoreException {
		ResourcesPlugin.getWorkspace().run(new ICoreRunnable() {
			@Override
			public void run(final @Nullable IProgressMonitor monitor) throws CoreException {
				final Set<Entry<IPath, DocumentData<?>>> allData = Plugin.getAllData();
				final SubMonitor sm = SubMonitor.convert(monitor, allData.size());
				for (final Entry<IPath, DocumentData<?>> data : allData) {
					final IFile file = ResourcesPlugin.getWorkspace().getRoot().getFile(data.getKey());
					if (file != null)
						linkNoRunnable(file, data.getValue());
					if (sm.isCanceled())
						throw new OperationCanceledException();
					sm.worked(1);
				}
				sm.done();
			}
		}, null, IWorkspace.AVOID_UPDATE, monitor);
	}
	
	public final static void link(final IResource file, final DocumentData<?> data) throws CoreException {
		ResourcesPlugin.getWorkspace().run(new ICoreRunnable() {
			@Override
			public void run(final @Nullable IProgressMonitor monitor) throws CoreException {
				linkNoRunnable(file, data);
			}
		}, null, IWorkspace.AVOID_UPDATE, null);
	}
	
	private final static void linkNoRunnable(final IResource file, final DocumentData<?> data) throws CoreException {
		file.deleteMarkers(Plugin.LINKER_ERROR_ID, true, IResource.DEPTH_INFINITE);
		final Consumer<LinkerError> errors = (final LinkerError e) -> {
			final WordOrSymbols nameToken = e.link.getNameToken();
			if (nameToken == null)
				return;
			try {
				final IMarker m = file.createMarker(Plugin.LINKER_ERROR_ID);
				m.setAttribute(IMarker.SEVERITY, IMarker.SEVERITY_ERROR);
				m.setAttribute(IMarker.MESSAGE, "Cannot find '" + e.link.getName() + "'");
				m.setAttribute(IMarker.CHAR_START, nameToken.absoluteRegionStart());
				m.setAttribute(IMarker.CHAR_END, nameToken.absoluteRegionEnd());
//				m.setAttribute(IMarker.LINE_NUMBER, data.reader.getLine(nameToken.absoluteRegionStart()));
				m.setAttribute(IMarker.PRIORITY, IMarker.PRIORITY_HIGH);
				m.setAttribute(IMarker.USER_EDITABLE, false);
			} catch (final CoreException ex) {}
		};
		Linker.link(data.ast, errors);
	}
	
	private final static @Nullable String readFile(final IFile file) throws CoreException {
		try (InputStream in = file.getContents()) {
			final ByteArrayOutputStream result = new ByteArrayOutputStream();
			final byte[] buffer = new byte[1024];
			int length;
			while ((length = in.read(buffer)) != -1) {
				result.write(buffer, 0, length);
			}
			return "" + result.toString(file.getCharset());
		} catch (final UnsupportedEncodingException e) {
			// TODO warn
		} catch (final IOException e) {
			// TODO warn
		}
		return null;
	}
	
	public final static void deleteMarkers(final IResource resource) {
		try {
			// delete all markers
			resource.deleteMarkers(IMarker.PROBLEM, true, IResource.DEPTH_INFINITE);
			resource.deleteMarkers(IMarker.TASK, true, IResource.DEPTH_INFINITE);
		} catch (final CoreException ex) {}
	}
	
	// TODO do this like the JDT (configurable by user)
	final static Pattern commentTaskKeywords = Pattern.compile("\\b(FIXME|BUG|TODO|LANG|REM(?:IND)?)\\b");
	
	public final static void createSyntaxMarkers(final DocumentData<?> data, final IResource file) {
		createSyntaxMarkers(data.reader, data.tokens, data.astDocument, file);
	}
	
	/**
	 * Creates markers that only depend on the syntax (in particular: syntax errors and comment tasks)
	 * 
	 * @param reader
	 * @param tokens
	 * @param ast
	 * @param file
	 */
	public final static void createSyntaxMarkers(final SourceReader reader, final TokenList tokens, final ASTDocument<?> ast, final IResource file) {
		try {
			file.getWorkspace().run(new ICoreRunnable() {
				@Override
				public void run(final @Nullable IProgressMonitor monitor) throws CoreException {
					for (final ParseError e : ast.fatalParseErrors()) {
						try {
							final Map<String, Object> attributes = new HashMap<>();
							attributes.put("" + IMarker.SEVERITY, IMarker.SEVERITY_ERROR);
							attributes.put("" + IMarker.CHAR_START, e.start);
							attributes.put("" + IMarker.CHAR_END, e.start + e.length);
							attributes.put("" + IMarker.MESSAGE, e.message);
							attributes.put("" + IMarker.LINE_NUMBER, reader.getLine(e.start));
							attributes.put("" + IMarker.PRIORITY, IMarker.PRIORITY_HIGH);
							attributes.put("" + IMarker.USER_EDITABLE, false);
							final IMarker m = file.createMarker(Plugin.SYNTAX_ERROR_ID);
							m.setAttributes(attributes);
						} catch (final CoreException ex) {}
					}
					
					for (final Token t : tokens) {
						if (t instanceof CommentToken) {
							final Matcher m = commentTaskKeywords.matcher(((CommentToken) t).comment);
							while (m.find()) {
								final int start = m.start();
								final Matcher m2 = Pattern.compile("$", Pattern.MULTILINE).matcher(((CommentToken) t).comment);
								m2.find(start);
								final int end = m2.end();
								try {
									final Map<String, Object> attributes = new HashMap<>();
									attributes.put("" + IMarker.SEVERITY, IMarker.SEVERITY_INFO);
									attributes.put("" + IMarker.CHAR_START, t.absoluteRegionStart() + start);
									attributes.put("" + IMarker.CHAR_END, t.absoluteRegionStart() + end);
									attributes.put("" + IMarker.MESSAGE, "" + ((CommentToken) t).comment.substring(start, end));
									attributes.put("" + IMarker.LINE_NUMBER, reader.getLine(start));
									attributes.put("" + IMarker.PRIORITY, "FIXME".equals(m.group(1)) || "BUG".equals(m.group(1)) ? IMarker.PRIORITY_HIGH
											: "REM".equals(m.group(1)) || "REMIND".equals(m.group(1)) ? IMarker.PRIORITY_LOW
													: IMarker.PRIORITY_NORMAL);
									attributes.put("" + IMarker.USER_EDITABLE, false);
									final IMarker marker = file.createMarker(IMarker.TASK); // make own marker type?
									marker.setAttributes(attributes);
								} catch (final CoreException ex) {}
							}
						}
					}
				}
			}, null, IWorkspace.AVOID_UPDATE, null);
		} catch (final CoreException ex) {}
	}
	
}
