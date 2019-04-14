package ch.njol.tome.eclipse;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.debug.core.ILaunch;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.debug.core.model.ILaunchConfigurationDelegate2;
import org.eclipse.jdt.annotation.Nullable;

public class LaunchConfigurationDelegate implements ILaunchConfigurationDelegate2 {
	
	private final static String BASE_ID = Plugin.ID + ".launchconfiguration.";
	
	public final static String KEY_PROJECT = BASE_ID + "project";
	public final static String KEY_MAIN_CLASS = BASE_ID + "main-class";
	
	@Override
	public void launch(ILaunchConfiguration configuration, String mode, ILaunch launch, @Nullable IProgressMonitor monitor) throws CoreException {
//		launch.addProcess(new RuntimeProcess(launch, process, name, attributes));
		launch.addProcess(new InterpreterProcess(configuration, mode, launch, monitor));
	}
	
	@Override
	public @Nullable ILaunch getLaunch(ILaunchConfiguration configuration, String mode) throws CoreException {
		return null;
	}
	
	@Override
	public boolean buildForLaunch(ILaunchConfiguration configuration, String mode, @Nullable IProgressMonitor monitor) throws CoreException {
		return true;
	}
	
	@Override
	public boolean finalLaunchCheck(ILaunchConfiguration configuration, String mode, @Nullable IProgressMonitor monitor) throws CoreException {
		return true;
	}
	
	@Override
	public boolean preLaunchCheck(ILaunchConfiguration configuration, String mode, @Nullable IProgressMonitor monitor) throws CoreException {
		return true;
	}
	
}
