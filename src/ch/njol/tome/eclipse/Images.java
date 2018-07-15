package ch.njol.tome.eclipse;

import org.eclipse.core.runtime.FileLocator;
import org.eclipse.core.runtime.Path;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.jface.resource.ImageRegistry;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.ImageData;

public enum Images {
	
	fileIcon("brokkrfile"),//
	moduleIcon("module"),//
	
	classIcon("class"),//
	interfaceIcon("interface"),//
	extensionIcon("extension"),//
	enumIcon("enum"),//
	enumElementIcon("enumElement"),//
	
	templateIcon("template"),//
	
	staticAttributeIcon("staticAttribute"),//
	instanceAttributeIcon("instanceAttribute"),//
	variableInstanceAttributeIcon("variableInstanceAttribute"),//
	constructorIcon("constructor"),//
	
	overlay_new("overlay_new"),//
	overlay_renamed("overlay_renamed"),//
	// TODO overlay for partialOverride - or make a base icon for that?
	
	;
	
	private final String name;
	
	private Images(final String name) {
		this.name = name;
	}
	
	public Image get() {
		Image r = registry.get(name);
		if (r == null)
			registry.put(name, r = ImageDescriptor.createFromURL(FileLocator.find(Plugin.bundle(), new Path("./icons/" + name + ".png"), null)).createImage());
		assert r != null;
		return r;
	}
	
	/**
	 * @param overlays the images to overlay, may contain null pointers
	 * @return
	 */
	public Image withOverlays(final @Nullable Images... overlays) {
//		Arrays.sort(images, (a,b) -> a.name.compareTo(b.name)); // maybe the order is wanted
		String id = name;
		for (int i = 0; i < overlays.length; i++) {
			final Images overlay = overlays[i];
			if (overlay != null)
				id += "#" + overlay.name;
		}
		if (id == name)
			return get();
		Image r = registry.get(id);
		if (r == null) {
			final ImageData data = get().getImageData();
			final int[] pixels1 = new int[data.width * data.height];
			data.getPixels(0, 0, pixels1.length, pixels1, 0);
			final byte[] alphas1 = new byte[data.width * data.height];
			data.getAlphas(0, 0, alphas1.length, alphas1, 0);
			for (int i = 0; i < overlays.length; i++) {
				final Images overlay = overlays[i];
				if (overlay == null)
					continue;
				final ImageData data2 = overlay.get().getImageData();
				assert data.width == data2.width && data.height == data2.height : name + " [" + data.width + "x" + data.height + "]" + " / " + overlay.name + " [" + data2.width + "x" + data2.height + "]";
				final int[] pixels2 = new int[data.width * data.height];
				data2.getPixels(0, 0, pixels2.length, pixels2, 0);
				final byte[] alphas2 = new byte[data.width * data.height];
				data2.getAlphas(0, 0, alphas2.length, alphas2, 0);
				for (int p = 0; p < pixels1.length; p++) {
					final double alpha1 = (alphas1[p] & 0xFF) / 255.0, alpha2 = (alphas2[p] & 0xFF) / 255.0;
					if (alpha2 == 0)
						continue;
					pixels1[p] = (int) (pixels1[p] * alpha1 * (1 - alpha2) + pixels2[p] * alpha2);
					alphas1[p] = (byte) ((int) (0xFF * (1 - (1 - alpha1) * (1 - alpha2))));
					pixels1[p] = (int) (pixels1[p] / ((alphas1[p] & 0xFF) / 255.0)); // un-multiply alpha
				}
			}
			data.setPixels(0, 0, pixels1.length, pixels1, 0);
			data.setAlphas(0, 0, alphas1.length, alphas1, 0);
			r = new Image(get().getDevice(), data);
			registry.put(id, r);
		}
		return r;
	}
	
	private static ImageRegistry registry = new ImageRegistry();
	
}
