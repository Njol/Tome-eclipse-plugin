package ch.njol.tome.eclipse.preferences.pages;

import org.eclipse.ui.IWorkbenchPreferencePage;

public abstract class AbstractPreferencePage implements IWorkbenchPreferencePage {
	
	@Override
	public boolean okToLeave() {
		return isValid();
	}
	
}
