package ch.njol.tome.eclipse;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.debug.core.ILaunchConfigurationWorkingCopy;
import org.eclipse.debug.ui.AbstractLaunchConfigurationTab;
import org.eclipse.debug.ui.AbstractLaunchConfigurationTabGroup;
import org.eclipse.debug.ui.CommonTab;
import org.eclipse.debug.ui.ILaunchConfigurationDialog;
import org.eclipse.debug.ui.ILaunchConfigurationTab;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.PlatformUI;

public class LaunchConfigurationTabGroup extends AbstractLaunchConfigurationTabGroup {
	
	@Override
	public void createTabs(ILaunchConfigurationDialog dialog, String mode) {
		setTabs(new ILaunchConfigurationTab[] {
				new Tab1(), new CommonTab()
		});
	}
	
	@SuppressWarnings("null")
	private static class Tab1 extends AbstractLaunchConfigurationTab {
		
		private @Nullable Text classToRun;
		
		@Override
		public void createControl(Composite parent) {
			Composite comp = new Composite(parent, SWT.NONE);
			setControl(comp);
			PlatformUI.getWorkbench().getHelpSystem().setHelp(getControl(), getHelpContextId());
			comp.setLayout(new GridLayout(2, true));
			comp.setFont(parent.getFont());
			
			// TODO project input field? (the same module may exist in several projects!)
			
			classToRun = new Text(comp, SWT.SINGLE | SWT.BORDER);
			classToRun.setFont(comp.getFont());
			GridData gd = new GridData(GridData.FILL_HORIZONTAL);
			gd.horizontalSpan = 1;
			classToRun.setLayoutData(gd);
		}
		
		@Override
		public void setDefaults(ILaunchConfigurationWorkingCopy configuration) {}
		
		@Override
		public void initializeFrom(ILaunchConfiguration configuration) {
			try {
				classToRun.setText(configuration.getAttribute(LaunchConfigurationDelegate.KEY_MAIN_CLASS, ""));
			} catch (CoreException e) {}
		}
		
		@Override
		public void performApply(ILaunchConfigurationWorkingCopy configuration) {
			configuration.setAttribute(LaunchConfigurationDelegate.KEY_MAIN_CLASS, classToRun.getText());
		}
		
		@Override
		public String getName() {
			return "Main";
		}
		
		@Override
		public @Nullable Image getImage() {
			return Images.fileIcon.get();
		}
		
	}
	
}
