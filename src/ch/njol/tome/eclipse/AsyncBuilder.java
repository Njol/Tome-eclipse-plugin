package ch.njol.tome.eclipse;

import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Set;

import ch.njol.tome.Constants;
import ch.njol.tome.eclipse.Plugin.DocumentData;

/**
 * Currently only used to update markers - parsing and updating the outline is fast enough to be done in the event handler (for now).
 * 
 * @author Peter Güttinger
 */
public class AsyncBuilder {
	
	private static AsyncBuilder INSTANCE = new AsyncBuilder();
	
	private Set<DocumentData<?>> buildQueue = new LinkedHashSet<>();
	
	private Thread thread;
	
	private volatile boolean running = true;
	
	private AsyncBuilder() {
		thread = new Thread(this::run);
		thread.setName(Constants.LANGUAGE_NAME + " async builder thread");
		thread.start();
	}
	
	private void run() {
		outer: while (running) {
			DocumentData<?> data;
			synchronized (buildQueue) {
				while (buildQueue.isEmpty()) {
					try {
						buildQueue.wait();
					} catch (InterruptedException e) {
						continue outer;
					}
				}
				Iterator<DocumentData<?>> iter = buildQueue.iterator();
				data = iter.next();
				iter.remove();
			}
//			data.update(0, data.reader.getLength());
//			data.update(event.getOffset(), Math.max(event.getLength(), event.getText().length()));
//			editor.outlineChanged();
			// FIXME make properly concurrent (lock?, copy?)
			Builder.updateMarkersAndLink(data.file, data);
		}
	}
	
	public static void notifyChange(DocumentData<?> data) {
		INSTANCE._notifyChange(data);
	}
	
	public void _notifyChange(DocumentData<?> data) {
		if (!running)
			return;
		synchronized (buildQueue) {
			buildQueue.add(data);
			buildQueue.notifyAll();
		}
	}
	
	public static void stop() {
		INSTANCE.running = false;
		synchronized (INSTANCE.buildQueue) {
			INSTANCE.buildQueue.clear();
		}
		INSTANCE.thread.interrupt();
	}
	
}
