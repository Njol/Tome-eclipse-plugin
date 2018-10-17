package ch.njol.tome.eclipse;

import static ch.njol.tome.Constants.*;

import java.lang.reflect.InvocationTargetException;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IProjectDescription;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.wizard.Wizard;
import org.eclipse.jface.wizard.WizardPage;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.INewWizard;
import org.eclipse.ui.IWorkbench;

public class NewProjectWizard extends Wizard implements INewWizard {
	
	@Override
	public void init(@SuppressWarnings("null") final IWorkbench workbench, @SuppressWarnings("null") final IStructuredSelection selection) {}
	
	private class Page extends WizardPage {
		
		private Text name;
		private @Nullable IProject resultProject;
		
		@SuppressWarnings("null")
		protected Page() {
			super("page1");
			setTitle("New " + LANGUAGE_NAME + " module");
		}
		
		@Override
		public void createControl(@SuppressWarnings("null") final Composite parent) {
			final Composite container = new Composite(parent, SWT.NULL);
			final GridLayout layout = new GridLayout();
			container.setLayout(layout);
			layout.numColumns = 2;//3;
			layout.verticalSpacing = 9;
			
			{
				final Label label = new Label(container, SWT.NULL);
				label.setText("&Name:");
				name = new Text(container, SWT.BORDER | SWT.SINGLE);
				final GridData gd = new GridData(GridData.FILL_HORIZONTAL);
				name.setLayoutData(gd);
				name.addModifyListener(e -> dialogChanged());
			}
			
			initialize();
			
			dialogChanged();
			setControl(container);
			
		}
		
		private void initialize() {}
		
		private void dialogChanged() {
			final String fileName = name.getText();
			if (fileName.isEmpty() || fileName.contains("/") || fileName.contains("\\")) {
				updateStatus("Invalid name");
				return;
			}
			resultProject = ResourcesPlugin.getWorkspace().getRoot().getProject(fileName);
			if (resultProject != null && resultProject.exists()) {
				updateStatus("Project with this name already exists");
				resultProject = null;
				return;
			}
			updateStatus(null);
		}
		
		private void updateStatus(@Nullable final String message) {
			setErrorMessage(message);
			setPageComplete(message == null);
		}
		
	}
	
	private @Nullable Page page;
	
	@Override
	public void addPages() {
		addPage(page = new Page());
	}
	
	@SuppressWarnings("null")
	@Override
	public boolean performFinish() {
		try {
			getContainer().run(true, false, monitor -> {
				try {
					doFinish(page.resultProject, monitor);
				} catch (final CoreException e) {
					throw new InvocationTargetException(e);
				} finally {
					monitor.done();
				}
			});
			return true;
		} catch (final InterruptedException e) {
			e.printStackTrace();
			return false;
		} catch (final InvocationTargetException e) {
			e.printStackTrace();
			final Throwable realException = e.getTargetException();
			MessageDialog.openError(getShell(), "Error", realException.getMessage());
			return false;
		}
	}
	
	private static void doFinish(final IProject project, final IProgressMonitor monitor) throws CoreException {
		monitor.beginTask("Creating " + project.getName(), 1);
		
		// create project
		final IProjectDescription desc = project.getWorkspace().newProjectDescription(project.getName());
		project.create(desc, null);
		if (!project.isOpen())
			project.open(null);
		
		// assign nature
		final IProjectDescription description = project.getDescription();
		final String[] natures = description.getNatureIds();
		final String[] newNatures = new @NonNull String[natures.length + 1];
		System.arraycopy(natures, 0, newNatures, 0, natures.length);
		newNatures[natures.length] = Nature.NATURE_ID;
		description.setNatureIds(newNatures);
		project.setDescription(description, null);
		
		// make a src folder
		project.getFolder("src").create(false, true, null);
		
		monitor.worked(1);
	}
	
}
