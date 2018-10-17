package ch.njol.tome.eclipse;

import org.eclipse.core.resources.IMarker;
import org.eclipse.ui.IMarkerResolution;
import org.eclipse.ui.IMarkerResolutionGenerator;

public class MarkerResolutionGenerator implements IMarkerResolutionGenerator {
	
	@Override
	public IMarkerResolution[] getResolutions(final IMarker marker) {
		return new IMarkerResolution[0];
	}
	
}
