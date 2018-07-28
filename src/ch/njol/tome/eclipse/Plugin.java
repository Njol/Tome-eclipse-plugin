package ch.njol.tome.eclipse;

import java.util.Iterator;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.Platform;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.ui.plugin.AbstractUIPlugin;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;

import ch.njol.tome.ast.ASTDocument;
import ch.njol.tome.ast.ASTElement;
import ch.njol.tome.ast.ASTTopLevelElements.ASTSourceFile;
import ch.njol.tome.common.ModuleIdentifier;
import ch.njol.tome.compiler.Lexer;
import ch.njol.tome.compiler.Modules;
import ch.njol.tome.compiler.SourceReader;
import ch.njol.tome.compiler.TokenList;
import ch.njol.tome.eclipse.Plugin.DocumentData.DocumentDataParser;
import ch.njol.tome.moduleast.ASTModule;

public class Plugin extends AbstractUIPlugin {
	
	public final static String ID = "ch.njol.tome.eclipse.plugin";
	public final static String SYNTAX_ERROR_ID = ID + ".syntaxError";
	public final static String LINKER_ERROR_ID = ID + ".linkerError";
	
	public Plugin() {}
	
//	@SuppressWarnings("null")
//	private static Plugin instance = null;
	
	@Override
	public void start(@Nullable final BundleContext context) throws Exception {
		super.start(context);
//		assert instance == null;
//		instance = this;
	}
	
	@Override
	public void stop(@Nullable final BundleContext context) throws Exception {
		super.stop(context);
//		instance = null;
	}
	
	@SuppressWarnings("null")
	public static Bundle bundle() {
		return Platform.getBundle(ID);
	}
	
	// FIXME make one per project?
	final static Modules modules = new Modules("<super modules>");
	
	public static @Nullable ASTModule getModule(final ModuleIdentifier id) {
		return modules.get(id); 
	}
	
	public final static DocumentDataParser<ASTSourceFile> sourceFileParser(final IFile file) {
		return data -> ASTSourceFile.parseFile(modules, "" + file.getFullPath().toString(), data.tokens);
	}
	
	@SuppressWarnings("null")
	public final static DocumentDataParser<ASTModule> moduleParser = data -> {
		if (data.ast != null) // happens in the constructor
			modules.unregister(data.ast);
		final ASTDocument<ASTModule> m = ASTModule.load(modules, data.tokens.stream());
		modules.register(m.root());
		return m;
	};
	
	public final static class DocumentData<T extends ASTElement> {
		public static interface DocumentDataParser<T extends ASTElement> {
			public ASTDocument<T> parse(DocumentData<T> data);
		}
		
		public DocumentData(final SourceReader reader, final DocumentDataParser<T> parser) {
			this.reader = reader;
			lexer = new Lexer(reader);
			tokens = lexer.list();
			this.parser = parser;
			astDocument = parser.parse(this);
			ast = astDocument.root();
		}
		
		public SourceReader reader;
		public Lexer lexer;
		public TokenList tokens;
		public DocumentDataParser<T> parser;
		public ASTDocument<T> astDocument;
		public T ast;
		
		// TODO make this incremental
		public void update(final int start, final int length) {
			//lexer.update(start, length);
			lexer.update(0, reader.getLength());
			tokens = lexer.list();
			ast.invalidateSubtree();
			astDocument = parser.parse(this);
			ast = astDocument.root();
		}
		
		public void update(final SourceReader reader) {
			if (reader.equals(this.reader))
				return;
			this.reader = reader;
			lexer = new Lexer(reader);
			tokens = lexer.list();
			ast.invalidateSubtree();
			astDocument = parser.parse(this);
			ast = astDocument.root();
		}
	}
	
	public final static ConcurrentMap<IPath, DocumentData<?>> documentData = new ConcurrentHashMap<>();
	
	@SuppressWarnings("unchecked")
	public static <T extends ASTElement> DocumentData<T> getData(final IPath file, final SourceReader reader, final DocumentDataParser<? extends T> parser) {
		DocumentData<?> d = documentData.get(file);
		if (d == null) {
			documentData.put(file, d = new DocumentData<>(reader, parser));
		} else {
			assert (parser == moduleParser) == (d.parser == moduleParser) : file + " / " + d.ast.getClass() + ", " + (parser == moduleParser);
			d.update(reader);
		}
		return (DocumentData<T>) d;
	}
	
	public static @Nullable DocumentData<?> getData(final IPath file) {
		return documentData.get(file);
	}
	
	public static Set<Entry<IPath, DocumentData<?>>> getAllData() {
		return documentData.entrySet();
	}
	
	public static @Nullable DocumentData<?> removeData(final IPath file) {
		final DocumentData<?> data = documentData.remove(file);
		if (data != null)
			data.ast.invalidateSubtree();
		return data;
	}
	
	public static void removeAllProjectData(final IProject project) {
		for (final Entry<IPath, DocumentData<?>> e : documentData.entrySet()) {
			if (e.getKey().segment(0).equals(project.getName()) && e.getValue().ast instanceof ASTModule) {
				final ASTModule ast = (ASTModule) e.getValue().ast;
				modules.unregister(ast);
			}
		}
		final Iterator<Entry<IPath, DocumentData<?>>> iterator = documentData.entrySet().iterator();
		while (iterator.hasNext()) {
			final Entry<IPath, DocumentData<?>> entry = iterator.next();
			if (entry.getKey().segment(0).equals(project.getName())) {
				entry.getValue().ast.invalidateSubtree();
				iterator.remove();
			}
		}
	}
	
	public static @Nullable IFile getFile(final ASTElement element) {
		final ASTSourceFile bf = element.getParentOrSelfOfType(ASTSourceFile.class);
		if (bf != null)
			return ResourcesPlugin.getWorkspace().getRoot().getFile(new Path(bf.identifier));
		final ASTModule m = element.getParentOrSelfOfType(ASTModule.class);
		if (m == null)
			return null;
		final ModuleIdentifier m_id = m.id;
		if (m_id == null)
			return null;
		for (final Entry<IPath, DocumentData<?>> e : documentData.entrySet()) {
			final ASTElement ast = e.getValue().ast;
			if (ast instanceof ASTModule && m_id.equals(((ASTModule) ast).id))
				return ResourcesPlugin.getWorkspace().getRoot().getFile(e.getKey());
		}
		return null;
	}
	
//	public static DocumentData getData(final IDocument document) {
//		for (DocumentData data : documentData.values()) {
//			if (data.reader instanceof DocumentReader && ((DocumentReader) data.reader).document == document)
//				return data;
//		}
//		return new DocumentData(new DocumentReader(document));
//	}

}
