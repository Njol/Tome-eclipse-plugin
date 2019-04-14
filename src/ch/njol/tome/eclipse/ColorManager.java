package ch.njol.tome.eclipse;

import java.util.HashMap;
import java.util.Map;

import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.RGB;
import org.eclipse.swt.widgets.Display;

public class ColorManager {
	
	protected Map<RGB, Color> colorTable = new HashMap<>();
	
	public void dispose() {
		for (final Color c : colorTable.values())
			c.dispose();
		colorTable.clear();
	}
	
	public Color getColor(final RGB rgb) {
		Color color = colorTable.get(rgb);
		if (color == null) {
			color = new Color(Display.getCurrent(), rgb);
			colorTable.put(rgb, color);
		}
		return color;
	}
	
	private Color get(final int r, final int g, final int b) {
		return getColor(new RGB(r, g, b));
	}
	
	public final Color def() {
		return get(0, 0, 0);
	}
	
	public final Color keyword() {
		return get(0, 150, 150); // get(200, 50, 0);
	}
	
	public final Color comment() {
		return get(90, 150, 90);
	}
	
	public final Color commentTask() {
		return get(50, 150, 50);
	}
	
	public final Color symbol() {
		return get(200, 50, 0); // get(100, 50, 0);
	}
	
	public final Color parameter() {
		return get(0, 100, 0);
	}
	
	public final Color lambdaMethodCall() {
		return get(0, 0, 0);
	}
	
	public final Color localVariable() {
		return get(120, 60, 60);
	}
	
	public final Color unqualifiedAttribute() {
		return get(0, 70, 160);
	}
	
	public final Color codeGeneration() {
		return get(150, 150, 150);
	}
	
	public final Color string() {
		return get(0, 0, 200);
	}
	
	public final Color type() {
		return get(60, 0, 120);
	}
	
}
