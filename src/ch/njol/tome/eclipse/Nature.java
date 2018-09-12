package ch.njol.tome.eclipse;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.eclipse.core.resources.ICommand;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IProjectDescription;
import org.eclipse.core.resources.IProjectNature;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.jdt.annotation.Nullable;

public class Nature implements IProjectNature {
	
	private @Nullable IProject project;
	
	@Override
	public @Nullable IProject getProject() {
		return project;
	}
	
	@Override
	public void setProject(final @Nullable IProject project) {
		this.project = project;
	}
	
	public final static String BUILDER_ID = Plugin.BASE_ID + ".builder";
	public final static String NATURE_ID = Plugin.BASE_ID + ".nature";
	
	@Override
	public void configure() throws CoreException {
		final IProject project = this.project;
		if (project == null)
			return;
		
		// add builder to project
		final IProjectDescription desc = project.getDescription();
		final List<ICommand> commands = new ArrayList<>(Arrays.asList(desc.getBuildSpec()));
		if (!commands.stream().anyMatch(c -> c.getBuilderName().equals(BUILDER_ID))) {
			final ICommand command = desc.newCommand();
			command.setBuilderName(BUILDER_ID);
			commands.add(command);
			desc.setBuildSpec(commands.toArray(new @Nullable ICommand[commands.size()]));
			project.setDescription(desc, null);
		}
	}
	
	@Override
	public void deconfigure() throws CoreException {
		final IProject project = this.project;
		if (project == null)
			return;
		
		// remove builder from project
		final IProjectDescription desc = project.getDescription();
		final List<ICommand> commands = new ArrayList<>(Arrays.asList(desc.getBuildSpec()));
		if (commands.removeIf(c -> c.getBuilderName().equals(BUILDER_ID))) {
			desc.setBuildSpec(commands.toArray(new @Nullable ICommand[commands.size()]));
			project.setDescription(desc, null);
		}
	}
	
}
