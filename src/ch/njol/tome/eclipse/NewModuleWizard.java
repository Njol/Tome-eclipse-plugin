package ch.njol.tome.eclipse;

import static ch.njol.tome.Constants.*;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.lang.reflect.InvocationTargetException;
import java.nio.charset.StandardCharsets;

import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IAdaptable;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.Path;
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
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.ide.IDE;

import ch.njol.tome.common.ModuleIdentifier;
import ch.njol.tome.moduleast.ASTModule;

public class NewModuleWizard extends Wizard implements INewWizard {
	
	private @Nullable IContainer folder;
	
	@Override
	public void init(@SuppressWarnings("null") final IWorkbench workbench, @SuppressWarnings("null") final IStructuredSelection selection) {
		@Nullable
		Object selected = selection.getFirstElement();
		if (!(selected instanceof IResource) && selected instanceof IAdaptable)
			selected = ((IAdaptable) selected).getAdapter(IResource.class);
		if (selected instanceof IContainer)
			folder = (IContainer) selected;
		else if (selected instanceof IResource)
			folder = ((IResource) selected).getParent();
	}
	
	private class Page extends WizardPage {
		
		private Text containerText;
		private Text idText;
		private @Nullable IFile resultFile;
		
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
			
			// row 1 - folder
			{
				final Label label = new Label(container, SWT.NULL);
				label.setText("&Container:");
				containerText = new Text(container, SWT.BORDER | SWT.SINGLE);
				final GridData gd = new GridData(GridData.FILL_HORIZONTAL);
				containerText.setLayoutData(gd);
				containerText.addModifyListener(e -> dialogChanged());
//				final Button button = new Button(container, SWT.PUSH);
//				button.setText("Browse...");
//				button.addSelectionListener(new SelectionListener() {
//					@Override
//					public void widgetSelected(@SuppressWarnings("null") SelectionEvent e) {
//						browseFolder();
//					}
//
//					@Override
//					public void widgetDefaultSelected(@SuppressWarnings("null") SelectionEvent e) {}
//				});
			}
			
			// row 2 - file
			{
				final Label label = new Label(container, SWT.NULL);
				label.setText("&Module id:");
				idText = new Text(container, SWT.BORDER | SWT.SINGLE);
				final GridData gd = new GridData(GridData.FILL_HORIZONTAL);
				idText.setLayoutData(gd);
				idText.addModifyListener(e -> dialogChanged());
//				new Label(container, SWT.NULL);
			}
			
			initialize();
			
			dialogChanged();
			setControl(container);
			
		}
		
		private void initialize() {
			if (folder != null)
				containerText.setText(folder.getFullPath().toString());
		}
		
		private void dialogChanged() {
			// folder
			final IResource container = ResourcesPlugin.getWorkspace().getRoot().findMember(new Path(containerText.getText()));
			if (containerText.getText().isEmpty() || container == null || (container.getType() & (IResource.PROJECT | IResource.FOLDER)) == 0) {
				updateStatus("Invalid folder");
				return;
			}
			if (!container.isAccessible()) {
				updateStatus("Project must be writable");
				return;
			}
			
			// file
			final String id = idText.getText();
			if (id.isEmpty() || id.contains("/") || id.contains("\\")) {
				updateStatus("Invalid filename");
				return;
			}
			resultFile = ((IContainer) container).getFile(new Path(id + ".brokmod"));
			if (resultFile != null && resultFile.exists()) {
				updateStatus("File already exists");
				resultFile = null;
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
			final String id = page.idText.getText();
			getContainer().run(true, false, monitor -> {
				try {
					doFinish(new ModuleIdentifier(id), page.resultFile, monitor);
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
	
	private void doFinish(final ModuleIdentifier id, final IFile file, final IProgressMonitor monitor) throws CoreException {
		monitor.beginTask("Creating " + file.getName(), 1);
		final ASTModule module = new ASTModule(Plugin.modules, id);
		module.addDefaults();
		final ByteArrayOutputStream out = new ByteArrayOutputStream();
		try {
			module.save(new OutputStreamWriter(out, StandardCharsets.UTF_8));
		} catch (final IOException e) {}
		file.create(new ByteArrayInputStream(out.toByteArray()), true, monitor);
		getShell().getDisplay().asyncExec(() -> {
			try {
				IDE.openEditor(PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage(), file);
			} catch (final PartInitException e) {}
		});
		monitor.worked(1);
	}
	
}
